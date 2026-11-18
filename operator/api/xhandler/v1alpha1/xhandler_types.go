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

package v1alpha1

import (
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// XHandlerSpec is the desired state of an xhandler-service deployment: the api and worker roles
// (the Quarkus app with the Rust engine as a sidecar), the database migrations, the UI and,
// optionally, the egress proxy and the network policies.
type XHandlerSpec struct {
	// Custom xhandler-service image (the Quarkus app). The operator's default otherwise.
	// +optional
	Image string `json:"image,omitempty"`

	// Custom image of the engine, the sidecar that builds, signs and sends the SUNAT documents.
	// The operator's default otherwise.
	// +optional
	EngineImage string `json:"engineImage,omitempty"`

	// Custom image of the UI. The operator's default otherwise.
	// +optional
	UIImage string `json:"uiImage,omitempty"`

	// Secret(s) that might be used when pulling an image from a private container image registry or repository.
	// +optional
	ImagePullSecrets []corev1.LocalObjectReference `json:"imagePullSecrets,omitempty"`

	// The api role: REST, SOAP (OSE) and emission. It takes the traffic.
	// +optional
	API *RoleSpec `json:"api,omitempty"`

	// The worker role: runs the jobs (SUNAT sends, OSE forwarding, webhooks, reports). It takes no traffic.
	// +optional
	Worker *RoleSpec `json:"worker,omitempty"`

	// In this section you can find all properties related to connect to a database.
	// +optional
	Database *DatabaseSpec `json:"db,omitempty"`

	// In this section you can configure Oidc settings.
	// +optional
	OIDC *OIDCSpec `json:"oidc,omitempty"`

	// The key vault: an HSM through PKCS#11.
	// +optional
	Vault *VaultSpec `json:"vault,omitempty"`

	// Where the files (XML, CDR, PDF) are stored: an S3-compatible bucket.
	// +optional
	Storage *StorageSpec `json:"storage,omitempty"`

	// Base URL of the SUNAT fake, the sandbox every new company starts on. For instance, 'http://sunat-fake:8099'.
	// +optional
	SunatFakeURL string `json:"sunatFakeUrl,omitempty"`

	// The OSE role.
	// +optional
	OSE *OSESpec `json:"ose,omitempty"`

	// The CAPTCHA of the public validity lookup: the UI's widget and the app's verification.
	// Without it the lookup refuses every request (there is no test mode in production).
	// +optional
	Captcha *CaptchaSpec `json:"captcha,omitempty"`

	// Public URL of the API, for the buyers' links. Derived from the hostname when it is set.
	// +optional
	PublicURL string `json:"publicUrl,omitempty"`

	// Hostname the ingress serves.
	// +optional
	Hostname string `json:"hostname,omitempty"`

	// TLS of the ingress.
	// +optional
	TLS *TLSSpec `json:"tls,omitempty"`

	// The deployment is, by default, exposed through a basic ingress (to the UI, or to the API when the UI is disabled).
	// You can change this behaviour by setting the enabled property to false.
	// +optional
	Ingress *IngressSpec `json:"ingress,omitempty"`

	// An egress proxy (Squid) for SUNAT, third-party OSEs and webhooks: HTTPS to public addresses only.
	// Without it the app refuses webhooks unless XHANDLER_EGRESS_REQUIRE_PROXY is false (additionalOptions).
	// +optional
	EgressProxy *EgressProxySpec `json:"egressProxy,omitempty"`

	// Network policies that deny everything the pods don't need.
	// +optional
	NetworkPolicies *NetworkPoliciesSpec `json:"networkPolicies,omitempty"`

	// The UI.
	// +optional
	UI *UISpec `json:"ui,omitempty"`

	// The engine sidecar.
	// +optional
	Engine *EngineSpec `json:"engine,omitempty"`

	// OpenTelemetry: where the app and the engine export their traces.
	// +optional
	OTel *OTelSpec `json:"otel,omitempty"`

	// How the pods of the api, the worker, the UI and the egress proxy spread across zones:
	// Preferred (ScheduleAnyway, the default) or Required (DoNotSchedule, which needs zone labels
	// on the nodes).
	// +kubebuilder:validation:Enum=Preferred;Required
	// +kubebuilder:default=Preferred
	// +optional
	ZoneSpread string `json:"zoneSpread,omitempty"`

	// Configuration of the server (the app, and its migration Job),
	// expressed as a keys and values that can be either direct values or references to secrets.
	// +optional
	AdditionalOptions []ValueOrSecret `json:"additionalOptions,omitempty"`
}

// EngineSpec configures the engine sidecar.
type EngineSpec struct {
	// Configuration of the engine, expressed as a keys and values that can be either direct values
	// or references to secrets. An option replaces the operator's variable of the same name.
	// +optional
	AdditionalOptions []ValueOrSecret `json:"additionalOptions,omitempty"`
}

// OTelSpec configures OpenTelemetry.
type OTelSpec struct {
	// OTLP over HTTP endpoint of the collector, for the app and the engine. For instance,
	// 'http://otel-collector:4318'. Without it the app's OpenTelemetry SDK is disabled and the engine
	// exports nothing.
	// +optional
	Endpoint string `json:"endpoint,omitempty"`
}

// RoleSpec configures the deployment of a role.
type RoleSpec struct {
	// Number of instances. Default is 2.
	// +kubebuilder:default=2
	// +kubebuilder:validation:Minimum=0
	// +optional
	Instances *int32 `json:"instances,omitempty"`
}

// ValueOrSecret is a server option set either to a value or to the key of a secret.
type ValueOrSecret struct {
	// Name of the environment variable.
	// +kubebuilder:validation:MinLength=1
	// +required
	Name string `json:"name"`

	// +optional
	Value string `json:"value,omitempty"`

	// +optional
	Secret *corev1.SecretKeySelector `json:"secret,omitempty"`
}

// DatabaseSpec configures the connection to the database.
type DatabaseSpec struct {
	// The full database JDBC URL. For instance, 'jdbc:postgresql://xhandler-db-rw:5432/xhandler'.
	// +optional
	URL string `json:"url,omitempty"`

	// The reference to a secret holding the username the app logs in with: a non-owner member of xhandler_app.
	// +optional
	UsernameSecret *corev1.SecretKeySelector `json:"usernameSecret,omitempty"`

	// The reference to a secret holding the password the app logs in with.
	// +optional
	PasswordSecret *corev1.SecretKeySelector `json:"passwordSecret,omitempty"`

	// The reference to a secret holding the username of the schema owner. Only the migration Job uses it.
	// +optional
	OwnerUsernameSecret *corev1.SecretKeySelector `json:"ownerUsernameSecret,omitempty"`

	// The reference to a secret holding the password of the schema owner. Only the migration Job uses it.
	// +optional
	OwnerPasswordSecret *corev1.SecretKeySelector `json:"ownerPasswordSecret,omitempty"`
}

// OIDCSpec configures OIDC authentication.
type OIDCSpec struct {
	// Oidc server url. For instance, 'https://keycloak.example.com/realms/xhandler'.
	// +optional
	ServerURL string `json:"serverUrl,omitempty"`

	// Oidc client id.
	// +optional
	ClientID string `json:"clientId,omitempty"`

	// The reference to a secret holding the Oidc client secret.
	// +optional
	CredentialsSecret *corev1.SecretKeySelector `json:"credentialsSecret,omitempty"`
}

// VaultSpec configures the key vault: an HSM through PKCS#11.
type VaultSpec struct {
	// Path of the PKCS#11 module. For instance, '/opt/hsm/libCryptoki2.so' with the module volume.
	// +optional
	PKCS11Library string `json:"pkcs11Library,omitempty"`

	// The reference to a secret holding the PIN of the HSM.
	// +optional
	PKCS11PinSecret *corev1.SecretKeySelector `json:"pkcs11PinSecret,omitempty"`

	// A volume with the HSM vendor's PKCS#11 module, mounted at moduleMountPath of the app container.
	// +optional
	ModuleVolume *corev1.VolumeSource `json:"moduleVolume,omitempty"`

	// Where the module volume is mounted. Default is /opt/hsm.
	// +kubebuilder:default="/opt/hsm"
	// +optional
	ModuleMountPath string `json:"moduleMountPath,omitempty"`
}

// StorageSpec configures the S3-compatible bucket of the files.
type StorageSpec struct {
	// Endpoint of the S3 API. For instance, 'https://s3.us-east-1.amazonaws.com'.
	// +optional
	S3Endpoint string `json:"s3Endpoint,omitempty"`

	// Name of the bucket.
	// +optional
	Bucket string `json:"bucket,omitempty"`

	// Region of the bucket.
	// +optional
	Region string `json:"region,omitempty"`

	// The reference to a secret holding the access key.
	// +optional
	AccessKeySecret *corev1.SecretKeySelector `json:"accessKeySecret,omitempty"`

	// The reference to a secret holding the secret key.
	// +optional
	SecretKeySecret *corev1.SecretKeySelector `json:"secretKeySecret,omitempty"`
}

// CaptchaSpec configures the CAPTCHA of the public validity lookup.
type CaptchaSpec struct {
	// hcaptcha or turnstile (Cloudflare). Default is hcaptcha.
	// +kubebuilder:validation:Enum=hcaptcha;turnstile
	// +kubebuilder:default=hcaptcha
	// +optional
	Provider string `json:"provider,omitempty"`

	// The provider's public site key, for the UI's widget.
	// +kubebuilder:validation:MinLength=1
	// +required
	SiteKey string `json:"siteKey"`

	// The reference to a secret holding the provider's secret key, for the app's verification.
	// +required
	SecretKeySecret *corev1.SecretKeySelector `json:"secretKeySecret"`
}

// OSESpec configures the OSE role.
// +kubebuilder:validation:XValidation:rule="!has(self.ruc) || size(self.ruc) == 0 || (has(self.environment) && self.environment in ['beta', 'production'])",message="with a ruc, the environment must be beta or production"
type OSESpec struct {
	// The RUC the CDR-OSE are issued for. Without it the OSE intake answers "service unavailable".
	// +kubebuilder:validation:Pattern=`^[0-9]{11}$`
	// +optional
	RUC string `json:"ruc,omitempty"`

	// SUNAT environment the documents are forwarded to.
	// +kubebuilder:validation:Enum=fake;beta;production
	// +optional
	Environment string `json:"environment,omitempty"`

	// URL of SUNAT's OSE service, given at registration.
	// +optional
	ForwardURL string `json:"forwardUrl,omitempty"`
}

// TLSSpec configures the TLS of the ingress.
type TLSSpec struct {
	// A secret containing the TLS certificate of the hostname. Reference: https://kubernetes.io/docs/concepts/configuration/secret/#tls-secrets.
	// +optional
	Secret string `json:"secret,omitempty"`
}

// IngressSpec configures the ingress that exposes the deployment.
type IngressSpec struct {
	// +kubebuilder:default=true
	// +optional
	Enabled *bool `json:"enabled,omitempty"`

	// The IngressClass of the ingress. The cluster's default class otherwise.
	// +optional
	ClassName string `json:"className,omitempty"`
}

// EgressProxySpec configures the egress proxy.
type EgressProxySpec struct {
	// +kubebuilder:default=false
	// +optional
	Enabled *bool `json:"enabled,omitempty"`

	// Custom image of the proxy (Squid). The operator's default otherwise.
	// +optional
	Image string `json:"image,omitempty"`

	// Number of instances. Default is 2.
	// +kubebuilder:default=2
	// +kubebuilder:validation:Minimum=0
	// +optional
	Instances *int32 `json:"instances,omitempty"`

	// The UID Squid runs as. Default is 31: the image runs as root otherwise, which runAsNonRoot
	// refuses. On OpenShift (restricted-v2), set a UID of the namespace's range
	// (openshift.io/sa.scc.uid-range).
	// +kubebuilder:validation:Minimum=1
	// +optional
	RunAsUser *int64 `json:"runAsUser,omitempty"`
}

// NetworkPoliciesSpec configures the network policies.
// +kubebuilder:validation:XValidation:rule="!has(self.enabled) || !self.enabled || (has(self.ingressNamespaces) && size(self.ingressNamespaces) > 0)",message="enabled network policies need at least one ingress namespace"
type NetworkPoliciesSpec struct {
	// +kubebuilder:default=false
	// +optional
	Enabled *bool `json:"enabled,omitempty"`

	// Namespaces whose pods may reach the api and the UI on port 8080: the gateway or ingress
	// controller (for instance, ingress-nginx, or openshift-ingress on OpenShift), and the
	// monitoring. At least one when the policies are enabled.
	// +optional
	IngressNamespaces []string `json:"ingressNamespaces,omitempty"`

	// Egress rules added to the policy of the api and worker pods: for instance, the SUNAT fake or a
	// network HSM in the cluster.
	// +optional
	AdditionalEgress []networkingv1.NetworkPolicyEgressRule `json:"additionalEgress,omitempty"`

	// Selects the database pods of the namespace (for instance, cnpg.io/cluster: xhandler-db). Port
	// 5432 of any address otherwise.
	// +optional
	DatabasePodSelector *metav1.LabelSelector `json:"databasePodSelector,omitempty"`
}

// UISpec configures the UI.
type UISpec struct {
	// +kubebuilder:default=true
	// +optional
	Enabled *bool `json:"enabled,omitempty"`

	// Number of instances. Default is 2.
	// +kubebuilder:default=2
	// +kubebuilder:validation:Minimum=0
	// +optional
	Instances *int32 `json:"instances,omitempty"`

	// Name of a Searchpe of the namespace the UI looks RUCs and DNIs up in.
	// +optional
	SearchpeRef string `json:"searchpeRef,omitempty"`

	// The OIDC client of the UI: a public client with PKCE. Its tokens need an audience mapper that
	// adds the client id of oidc.clientId (xhandler-service's), which accepts them. Default is
	// xhandler-ui.
	// +kubebuilder:default=xhandler-ui
	// +optional
	OIDCClientID string `json:"oidcClientId,omitempty"`

	// The platform's own Libro de Reclamaciones (https), linked from every page of the UI
	// (Ley 32495). Not shown when empty.
	// +kubebuilder:validation:Pattern=`^https://`
	// +optional
	LibroReclamacionesURL string `json:"libroReclamacionesUrl,omitempty"`

	// A ConfigMap key with the CA certificates (PEM) the UI trusts on top of the system's
	// (NODE_EXTRA_CA_CERTS): the CA of a Searchpe with TLS, for instance.
	// +optional
	CABundleConfigMap *corev1.ConfigMapKeySelector `json:"caBundleConfigMap,omitempty"`
}

// XHandlerStatus is the observed state of an xhandler-service deployment.
type XHandlerStatus struct {
	// Migrated: the migration Job of the current image succeeded. Ready: the deployments are available.
	// +listType=map
	// +listMapKey=type
	// +optional
	Conditions []metav1.Condition `json:"conditions,omitempty"`

	// The generation of the spec the status describes.
	// +optional
	ObservedGeneration int64 `json:"observedGeneration,omitempty"`
}

// XHandler is a deployment of xhandler-service. Its name has at most 40 characters: the names of the
// resources it creates add up to 19 to it, and must be DNS labels (63 characters).
// +kubebuilder:object:root=true
// +kubebuilder:subresource:status
// +kubebuilder:printcolumn:name="Ready",type=string,JSONPath=`.status.conditions[?(@.type=="Ready")].status`
// +kubebuilder:printcolumn:name="Reason",type=string,JSONPath=`.status.conditions[?(@.type=="Ready")].reason`
// +kubebuilder:printcolumn:name="Age",type=date,JSONPath=`.metadata.creationTimestamp`
// +kubebuilder:validation:XValidation:rule="size(self.metadata.name) <= 40",message="the name must have at most 40 characters"
// +kubebuilder:validation:XValidation:rule="self.metadata.name.matches('^[a-z]([-a-z0-9]*[a-z0-9])?$')",message="the name must be a DNS-1035 label: lowercase letters, digits and '-', starting with a letter"
// +kubebuilder:validation:XValidation:rule="has(self.spec) && ((has(self.spec.ui) && has(self.spec.ui.enabled) && !self.spec.ui.enabled) || (has(self.spec.oidc) && has(self.spec.oidc.serverUrl) && size(self.spec.oidc.serverUrl) > 0))",message="the UI needs oidc.serverUrl"
type XHandler struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`

	// +optional
	Spec XHandlerSpec `json:"spec,omitempty"`

	// +optional
	Status XHandlerStatus `json:"status,omitempty"`
}

// XHandlerList is a list of XHandler.
// +kubebuilder:object:root=true
type XHandlerList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []XHandler `json:"items"`
}
