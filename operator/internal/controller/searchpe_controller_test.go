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
	"context"
	"testing"
	"time"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/wait"
	"k8s.io/utils/ptr"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/cache"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/config"
	metricsserver "sigs.k8s.io/controller-runtime/pkg/metrics/server"

	"github.com/project-openubl/xhandler-service/operator/api/v1alpha1"
)

func newSearchpe(t *testing.T, spec v1alpha1.SearchpeSpec) *v1alpha1.Searchpe {
	t.Helper()
	cr := &v1alpha1.Searchpe{
		ObjectMeta: metav1.ObjectMeta{Name: "searchpe", Namespace: newNamespace(t)},
		Spec:       spec,
	}
	if err := k8sClient.Create(t.Context(), cr); err != nil {
		t.Fatal(err)
	}
	return cr
}

func TestReconcileCreatesTheResources(t *testing.T) {
	ctx := t.Context()
	cr := newSearchpe(t, v1alpha1.SearchpeSpec{})
	reconcile(t, cr)

	deployment := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "searchpe-searchpe-deployment", deployment)
	service := &corev1.Service{}
	get(ctx, t, cr.Namespace, "searchpe-searchpe-service", service)
	ingress := &networkingv1.Ingress{}
	get(ctx, t, cr.Namespace, "searchpe-searchpe-ingress", ingress)
	secret := &corev1.Secret{}
	get(ctx, t, cr.Namespace, "searchpe-basic-auth-searchpe-secret", secret)

	for _, obj := range []client.Object{deployment, service, ingress, secret} {
		owner := metav1.GetControllerOf(obj)
		if owner == nil || owner.UID != cr.UID {
			t.Errorf("%s: controller %v, want the Searchpe", obj.GetName(), owner)
		}
	}
	if replicas := ptr.Deref(deployment.Spec.Replicas, 0); replicas != 1 {
		t.Errorf("replicas %d, want the default, 1", replicas)
	}
	if image := deployment.Spec.Template.Spec.Containers[0].Image; image != "ghcr.io/project-openubl/xhandler-service/searchpe:nightly" {
		t.Errorf("image %s, want the image of the operator's configuration", image)
	}
	if len(secret.Data[basicAuthEncryptionKey]) < 16 {
		t.Errorf("encryption key %q, want at least 16 characters", secret.Data[basicAuthEncryptionKey])
	}
}

func TestReconcileAppliesSpecChanges(t *testing.T) {
	ctx := t.Context()
	cr := newSearchpe(t, v1alpha1.SearchpeSpec{})
	reconcile(t, cr)

	cr.Spec.Image = "quay.io/example/searchpe:custom"
	cr.Spec.Instances = ptr.To[int32](0)
	cr.Spec.HTTP = &v1alpha1.HTTPSpec{TLSSecret: "tls"}
	if err := k8sClient.Update(ctx, cr); err != nil {
		t.Fatal(err)
	}
	reconcile(t, cr)

	deployment := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "searchpe-searchpe-deployment", deployment)
	if image := deployment.Spec.Template.Spec.Containers[0].Image; image != cr.Spec.Image {
		t.Errorf("image %s, want %s", image, cr.Spec.Image)
	}
	if replicas := ptr.Deref(deployment.Spec.Replicas, -1); replicas != 0 {
		t.Errorf("replicas %d, want 0", replicas)
	}
	service := &corev1.Service{}
	get(ctx, t, cr.Namespace, "searchpe-searchpe-service", service)
	if port := service.Spec.Ports[0].Port; port != httpsPort {
		t.Errorf("service port %d, want %d with TLS", port, httpsPort)
	}
}

func TestReconcileKeepsTheBasicAuthKey(t *testing.T) {
	ctx := t.Context()
	cr := newSearchpe(t, v1alpha1.SearchpeSpec{BasicAuth: &v1alpha1.BasicAuthSpec{Enabled: true}})
	reconcile(t, cr)
	before := &corev1.Secret{}
	get(ctx, t, cr.Namespace, "searchpe-basic-auth-searchpe-secret", before)

	reconcile(t, cr)
	after := &corev1.Secret{}
	get(ctx, t, cr.Namespace, "searchpe-basic-auth-searchpe-secret", after)

	if !bytes.Equal(before.Data[basicAuthEncryptionKey], after.Data[basicAuthEncryptionKey]) {
		t.Error("the encryption key changed")
	}
}

func TestReconcileDeletesTheDisabledIngress(t *testing.T) {
	ctx := t.Context()
	cr := newSearchpe(t, v1alpha1.SearchpeSpec{})
	reconcile(t, cr)
	get(ctx, t, cr.Namespace, "searchpe-searchpe-ingress", &networkingv1.Ingress{})

	cr.Spec.Ingress = &v1alpha1.IngressSpec{Enabled: ptr.To(false)}
	if err := k8sClient.Update(ctx, cr); err != nil {
		t.Fatal(err)
	}
	reconcile(t, cr)
	reconcile(t, cr)

	err := k8sClient.Get(ctx, client.ObjectKey{Namespace: cr.Namespace, Name: "searchpe-searchpe-ingress"}, &networkingv1.Ingress{})
	if !apierrors.IsNotFound(err) {
		t.Errorf("get the ingress: %v, want not found", err)
	}
}

func TestReconcileIgnoresADeletedSearchpe(t *testing.T) {
	cr := &v1alpha1.Searchpe{ObjectMeta: metav1.ObjectMeta{Name: "deleted", Namespace: newNamespace(t)}}
	reconcile(t, cr)
}

// The manager reconciles a Searchpe when it changes and when one of its resources changes.
func TestManagerWatchesTheResources(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	mgr, err := ctrl.NewManager(restConfig, ctrl.Options{
		Scheme:                 k8sClient.Scheme(),
		Metrics:                metricsserver.Options{BindAddress: "0"},
		HealthProbeBindAddress: "0",
		Controller:             config.Controller{SkipNameValidation: ptr.To(true)},
		// The cache of the operator, restricted for the XHandler controller
		Cache: cache.Options{ByObject: XHandlerCacheByObject()},
	})
	if err != nil {
		t.Fatal(err)
	}
	reconciler := newReconciler()
	reconciler.Client = mgr.GetClient()
	if err := reconciler.SetupWithManager(mgr); err != nil {
		t.Fatal(err)
	}
	go func() {
		if err := mgr.Start(ctx); err != nil {
			t.Error(err)
		}
	}()

	cr := newSearchpe(t, v1alpha1.SearchpeSpec{})
	key := client.ObjectKey{Namespace: cr.Namespace, Name: deploymentName(cr)}
	deployment := &appsv1.Deployment{}
	waitFor(t, "the deployment", func() bool { return k8sClient.Get(ctx, key, deployment) == nil })

	if err := k8sClient.Delete(ctx, deployment); err != nil {
		t.Fatal(err)
	}
	waitFor(t, "a new deployment", func() bool {
		recreated := &appsv1.Deployment{}
		return k8sClient.Get(ctx, key, recreated) == nil && recreated.UID != deployment.UID
	})
}

func waitFor(t *testing.T, what string, condition func() bool) {
	t.Helper()
	err := wait.PollUntilContextTimeout(t.Context(), 100*time.Millisecond, 10*time.Second, true,
		func(context.Context) (bool, error) { return condition(), nil })
	if err != nil {
		t.Fatalf("waiting for %s: %v", what, err)
	}
}
