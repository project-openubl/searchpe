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
	"crypto/rand"
	"maps"
	"strings"

	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	corev1ac "k8s.io/client-go/applyconfigurations/core/v1"
	metav1ac "k8s.io/client-go/applyconfigurations/meta/v1"
	networkingv1ac "k8s.io/client-go/applyconfigurations/networking/v1"

	"github.com/project-openubl/searchpe/operator/api/v1alpha1"
)

// The names, labels and selectors are the Java operator's: it created the resources that exist.
const (
	httpPort  = 8080
	httpsPort = 8443

	basicAuthEncryptionKey = "encryption-key"
)

// selectorLabels select the pods of the deployments. They select the pods of every Searchpe of the
// namespace, but the selector of a deployment can't change: the service selects the pods of its
// Searchpe with podLabels.
var selectorLabels = map[string]string{"app.kubernetes.io/managed-by": "searchpe-operator"}

func deploymentName(cr *v1alpha1.Searchpe) string { return cr.Name + "-searchpe-deployment" }
func serviceName(cr *v1alpha1.Searchpe) string    { return cr.Name + "-searchpe-service" }
func ingressName(cr *v1alpha1.Searchpe) string    { return cr.Name + "-searchpe-ingress" }
func basicAuthSecretName(cr *v1alpha1.Searchpe) string {
	return cr.Name + "-basic-auth-searchpe-secret"
}

// commonLabels are the labels of every resource of cr.
func commonLabels(cr *v1alpha1.Searchpe) map[string]string {
	return map[string]string{
		"app.kubernetes.io/name":   cr.Name,
		"openubl-operator/cluster": "searchpe",
	}
}

// podLabels are the labels of the pods of cr.
func podLabels(cr *v1alpha1.Searchpe) map[string]string {
	labels := commonLabels(cr)
	maps.Copy(labels, selectorLabels)
	return labels
}

func tlsEnabled(cr *v1alpha1.Searchpe) bool {
	return cr.Spec.HTTP != nil && strings.TrimSpace(cr.Spec.HTTP.TLSSecret) != ""
}

// ingressEnabled is true unless the ingress is disabled.
func ingressEnabled(cr *v1alpha1.Searchpe) bool {
	return cr.Spec.Ingress == nil || cr.Spec.Ingress.Enabled == nil || *cr.Spec.Ingress.Enabled
}

// servicePort is the port of the server: HTTPS when TLS is configured, HTTP otherwise.
func servicePort(cr *v1alpha1.Searchpe) int32 {
	if tlsEnabled(cr) {
		return httpsPort
	}
	return httpPort
}

func ownerReference(cr *v1alpha1.Searchpe) *metav1ac.OwnerReferenceApplyConfiguration {
	return metav1ac.OwnerReference().
		WithAPIVersion(v1alpha1.GroupVersion.String()).
		WithKind("Searchpe").
		WithName(cr.Name).
		WithUID(cr.UID).
		WithController(true).
		WithBlockOwnerDeletion(true)
}

func service(cr *v1alpha1.Searchpe) *corev1ac.ServiceApplyConfiguration {
	return corev1ac.Service(serviceName(cr), cr.Namespace).
		WithLabels(commonLabels(cr)).
		WithOwnerReferences(ownerReference(cr)).
		WithSpec(corev1ac.ServiceSpec().
			WithType(corev1.ServiceTypeClusterIP).
			WithSelector(podLabels(cr)).
			WithPorts(corev1ac.ServicePort().
				WithPort(servicePort(cr)).
				WithProtocol(corev1.ProtocolTCP)))
}

func ingress(cr *v1alpha1.Searchpe) *networkingv1ac.IngressApplyConfiguration {
	backendProtocol := "HTTP"
	if tlsEnabled(cr) {
		backendProtocol = "HTTPS"
	}
	backend := networkingv1ac.IngressBackend().
		WithService(networkingv1ac.IngressServiceBackend().
			WithName(serviceName(cr)).
			WithPort(networkingv1ac.ServiceBackendPort().WithNumber(servicePort(cr))))

	rule := networkingv1ac.IngressRule().
		WithHTTP(networkingv1ac.HTTPIngressRuleValue().
			WithPaths(networkingv1ac.HTTPIngressPath().
				WithPathType(networkingv1.PathTypeImplementationSpecific).
				WithBackend(backend)))
	if cr.Spec.Hostname != nil && cr.Spec.Hostname.Hostname != "" {
		rule.WithHost(cr.Spec.Hostname.Hostname)
	}

	return networkingv1ac.Ingress(ingressName(cr), cr.Namespace).
		WithLabels(commonLabels(cr)).
		WithAnnotations(map[string]string{
			"nginx.ingress.kubernetes.io/backend-protocol": backendProtocol,
			"route.openshift.io/termination":               "passthrough",
		}).
		WithOwnerReferences(ownerReference(cr)).
		WithSpec(networkingv1ac.IngressSpec().
			WithDefaultBackend(backend).
			WithRules(rule))
}

// exposedURL is the URL of the first address of the ingress, empty until it has one.
func exposedURL(cr *v1alpha1.Searchpe, ingress *networkingv1ac.IngressApplyConfiguration) string {
	if ingress.Status == nil || ingress.Status.LoadBalancer == nil || len(ingress.Status.LoadBalancer.Ingress) == 0 {
		return ""
	}
	address := ingress.Status.LoadBalancer.Ingress[0]
	host := address.Hostname
	if host == nil || *host == "" {
		host = address.IP
	}
	if host == nil || *host == "" {
		return ""
	}
	if tlsEnabled(cr) {
		return "https://" + *host
	}
	return "http://" + *host
}

// basicAuthSecret is a new secret with a random session encryption key. Its metadata is applied
// with basicAuthSecretMetadata.
func basicAuthSecret(cr *v1alpha1.Searchpe) *corev1.Secret {
	return &corev1.Secret{
		ObjectMeta: metav1.ObjectMeta{Name: basicAuthSecretName(cr), Namespace: cr.Namespace},
		Data:       map[string][]byte{basicAuthEncryptionKey: []byte(rand.Text())},
	}
}

// basicAuthSecretMetadata is the metadata of the secret of basicAuthSecret, without its key.
func basicAuthSecretMetadata(cr *v1alpha1.Searchpe) *corev1ac.SecretApplyConfiguration {
	return corev1ac.Secret(basicAuthSecretName(cr), cr.Namespace).
		WithLabels(commonLabels(cr)).
		WithOwnerReferences(ownerReference(cr))
}
