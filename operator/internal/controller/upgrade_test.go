/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controller

import (
	"bytes"
	"maps"
	"os"
	"strings"
	"testing"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/util/diff"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/yaml"

	"github.com/project-openubl/xhandler-service/operator/api/v1alpha1"
)

// The resources the Java operator created for a Searchpe (testdata/java/*.yaml: the Searchpe and
// what the last Java operator created for it, as it created them) must not change when this
// operator takes over, or the upgrade would restart Searchpe, or log its users out.
func TestUpgradeFromTheJavaOperator(t *testing.T) {
	for _, test := range []struct {
		fixture string
		// The Java operator created the ingress even when it was disabled
		ingressDisabled bool
	}{
		{fixture: "minimal"},
		{fixture: "full"},
		{fixture: "custom-key", ingressDisabled: true},
	} {
		t.Run(test.fixture, func(t *testing.T) {
			ctx := t.Context()
			cr := createJavaResources(t, "testdata/java/"+test.fixture+".yaml")

			deploymentBefore := &appsv1.Deployment{}
			get(ctx, t, cr.Namespace, deploymentName(cr), deploymentBefore)
			serviceBefore := &corev1.Service{}
			get(ctx, t, cr.Namespace, serviceName(cr), serviceBefore)
			ingressBefore := &networkingv1.Ingress{}
			get(ctx, t, cr.Namespace, ingressName(cr), ingressBefore)
			secretBefore := &corev1.Secret{}
			get(ctx, t, cr.Namespace, basicAuthSecretName(cr), secretBefore)

			reconcile(t, cr)

			deployment := &appsv1.Deployment{}
			get(ctx, t, cr.Namespace, deploymentName(cr), deployment)
			if deployment.Generation != deploymentBefore.Generation {
				t.Errorf("the deployment changed:\n%s", diff.Diff(deploymentBefore.Spec, deployment.Spec))
			}
			// The service selects the pods of its Searchpe only, the one change
			service := &corev1.Service{}
			get(ctx, t, cr.Namespace, serviceName(cr), service)
			if !maps.Equal(service.Spec.Selector, podLabels(cr)) {
				t.Errorf("service selector %v, want %v", service.Spec.Selector, podLabels(cr))
			}
			serviceBefore.Spec.Selector = service.Spec.Selector
			if d := diff.Diff(serviceBefore.Spec, service.Spec); d != "" {
				t.Errorf("the service changed:\n%s", d)
			}
			secret := &corev1.Secret{}
			get(ctx, t, cr.Namespace, basicAuthSecretName(cr), secret)
			if !bytes.Equal(secret.Data[basicAuthEncryptionKey], secretBefore.Data[basicAuthEncryptionKey]) {
				t.Error("the encryption key changed")
			}
			for _, obj := range []client.Object{deployment, service, secret} {
				if owner := metav1.GetControllerOf(obj); owner == nil || owner.UID != cr.UID {
					t.Errorf("%s: controller %v, want the Searchpe", obj.GetName(), owner)
				}
			}

			ingress := &networkingv1.Ingress{}
			err := k8sClient.Get(ctx, client.ObjectKey{Namespace: cr.Namespace, Name: ingressName(cr)}, ingress)
			switch {
			case test.ingressDisabled:
				if !apierrors.IsNotFound(err) {
					t.Errorf("get the disabled ingress: %v, want not found", err)
				}
			case err != nil:
				t.Fatal(err)
			case ingress.Generation != ingressBefore.Generation:
				t.Errorf("the ingress changed:\n%s", diff.Diff(ingressBefore.Spec, ingress.Spec))
			}
		})
	}
}

// createJavaResources creates the resources of a fixture in a new namespace, as the Java operator
// (Java Operator SDK 5, with quarkus-operator-sdk 7) did, which the capture of the fixtures showed:
// the Searchpe a plain owner (not the controller), the deployment, service and ingress
// server-side applied by the field manager searchpe, the secret created.
func createJavaResources(t *testing.T, fixture string) *v1alpha1.Searchpe {
	t.Helper()
	ctx := t.Context()
	data, err := os.ReadFile(fixture)
	if err != nil {
		t.Fatal(err)
	}
	documents := strings.Split(string(data), "\n---\n")
	namespace := newNamespace(t)

	objects := make([]*unstructured.Unstructured, len(documents))
	for i, document := range documents {
		objects[i] = &unstructured.Unstructured{}
		if err := yaml.Unmarshal([]byte(document), &objects[i].Object); err != nil {
			t.Fatalf("%s: %v", fixture, err)
		}
		objects[i].SetNamespace(namespace)
	}

	cr := &v1alpha1.Searchpe{}
	if err := k8sClient.Create(ctx, objects[0]); err != nil {
		t.Fatal(err)
	}
	get(ctx, t, namespace, objects[0].GetName(), cr)

	for _, obj := range objects[1:] {
		obj.SetOwnerReferences([]metav1.OwnerReference{{
			APIVersion: v1alpha1.GroupVersion.String(), Kind: "Searchpe", Name: cr.Name, UID: cr.UID,
		}})
		if obj.GetKind() == "Secret" {
			err = k8sClient.Create(ctx, obj)
		} else {
			err = k8sClient.Apply(ctx, client.ApplyConfigurationFromUnstructured(obj), fieldOwner, client.ForceOwnership)
		}
		if err != nil {
			t.Fatalf("%s %s: %v", obj.GetKind(), obj.GetName(), err)
		}
	}
	return cr
}
