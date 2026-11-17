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

	// Configuration of the server.
	// expressed as a keys and values that can be either direct values or references to secrets.
	// +optional
	AdditionalOptions []ValueOrSecret `json:"additionalOptions,omitempty"`
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
	// +optional
	Name string `json:"name,omitempty"`

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

// OSESpec configures the OSE role.
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
}

// NetworkPoliciesSpec configures the network policies.
type NetworkPoliciesSpec struct {
	// +kubebuilder:default=false
	// +optional
	Enabled *bool `json:"enabled,omitempty"`

	// Namespaces whose pods may reach the api and the UI on port 8080: the gateway or ingress
	// controller, and the monitoring. Default is envoy-gateway-system and monitoring.
	// +optional
	IngressNamespaces []string `json:"ingressNamespaces,omitempty"`

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
