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

// SearchpeSpec is the desired state of a Searchpe deployment.
type SearchpeSpec struct {
	// Number of instances. Default is 1.
	// +kubebuilder:default=1
	// +optional
	Instances *int32 `json:"instances,omitempty"`

	// Custom image to be used.
	// +optional
	Image string `json:"image,omitempty"`

	// Secret(s) that might be used when pulling an image from a private container image registry or repository.
	// +optional
	ImagePullSecrets []corev1.LocalObjectReference `json:"imagePullSecrets,omitempty"`

	// Configuration of the server.
	// expressed as a keys and values that can be either direct values or references to secrets.
	// +optional
	AdditionalOptions []ValueOrSecret `json:"additionalOptions,omitempty"`

	// In this section you can configure features related to HTTP and HTTPS
	// +optional
	HTTP *HTTPSpec `json:"http,omitempty"`

	// The deployment is, by default, exposed through a basic ingress.
	// You can change this behaviour by setting the enabled property to false.
	// +optional
	Ingress *IngressSpec `json:"ingress,omitempty"`

	// In this section you can find all properties related to connect to a database.
	// +optional
	Database *DatabaseSpec `json:"db,omitempty"`

	// In this section you can configure hostname and related properties.
	// +optional
	Hostname *HostnameSpec `json:"hostname,omitempty"`

	// In this section you can configure Basic Auth settings.
	// +optional
	BasicAuth *BasicAuthSpec `json:"basicAuth,omitempty"`

	// In this section you can configure Oidc settings.
	// +optional
	OIDC *OIDCSpec `json:"oidc,omitempty"`

	// In this section you can configure SUNAT settings.
	// +optional
	Sunat *SunatSpec `json:"sunat,omitempty"`
}

// ValueOrSecret is a server option set either to a value or to the key of a secret.
type ValueOrSecret struct {
	// +optional
	Name string `json:"name,omitempty"`

	// +optional
	Value string `json:"value,omitempty"`

	// +optional
	Secret *corev1.SecretKeySelector `json:"secret,omitempty"`
}

// HTTPSpec configures HTTP and HTTPS.
type HTTPSpec struct {
	// A secret containing the TLS configuration for HTTPS. Reference: https://kubernetes.io/docs/concepts/configuration/secret/#tls-secrets.
	// +optional
	TLSSecret string `json:"tlsSecret,omitempty"`
}

// IngressSpec configures the ingress that exposes the deployment.
type IngressSpec struct {
	// +kubebuilder:default=true
	// +optional
	Enabled *bool `json:"enabled,omitempty"`
}

// DatabaseSpec configures the connection to the database.
type DatabaseSpec struct {
	// The reference to a secret holding the username of the database user.
	// +optional
	UsernameSecret *corev1.SecretKeySelector `json:"usernameSecret,omitempty"`

	// The reference to a secret holding the password of the database user.
	// +optional
	PasswordSecret *corev1.SecretKeySelector `json:"passwordSecret,omitempty"`

	// The full database JDBC URL. For instance, 'jdbc:postgresql://localhost/searchpe'.
	// +optional
	URL string `json:"url,omitempty"`
}

// HostnameSpec configures the hostname of the server.
type HostnameSpec struct {
	// Hostname for the server.
	// +optional
	Hostname string `json:"hostname,omitempty"`
}

// BasicAuthSpec configures Basic Auth.
type BasicAuthSpec struct {
	// Enable Basic Auth.
	// +optional
	Enabled bool `json:"enabled,omitempty"`

	// The encryption key that is used to store persistent logins (e.g. for form auth). Logins are stored in a persistent cookie that is encrypted with AES-256 using a key derived from a SHA-256 hash of the key that is provided here.
	// +optional
	SessionEncryptionKeySecret *corev1.SecretKeySelector `json:"sessionEncryptionKeySecret,omitempty"`
}

// OIDCSpec configures OIDC authentication.
type OIDCSpec struct {
	// Enable Oidc Auth.
	// +optional
	Enabled bool `json:"enabled,omitempty"`

	// Oidc server url.
	// +optional
	ServerURL string `json:"serverUrl,omitempty"`

	// Oidc client id.
	// +optional
	ClientID string `json:"clientId,omitempty"`

	// The reference to a secret holding the Oidc client secret.
	// +optional
	CredentialsSecret *corev1.SecretKeySelector `json:"credentialsSecret,omitempty"`
}

// SunatSpec configures the download of the padrón reducido from SUNAT.
type SunatSpec struct {
	// Padron Reducido Url.
	// +optional
	PadronReducidoURL string `json:"padronReducidoUrl,omitempty"`

	// Cron for downloading the Padron Reducido.
	// +optional
	PadronReducidoCron string `json:"padronReducidoCron,omitempty"`
}

// SearchpeStatus is the observed state of a Searchpe deployment.
type SearchpeStatus struct {
	// +optional
	Conditions []SearchpeStatusCondition `json:"conditions,omitempty"`
}

// SearchpeStatusCondition is a condition of a Searchpe deployment.
type SearchpeStatusCondition struct {
	// +optional
	Type string `json:"type,omitempty"`

	// +optional
	Status *bool `json:"status,omitempty"`

	// +optional
	Message string `json:"message,omitempty"`
}

// Searchpe is a deployment of the Searchpe server.
// +kubebuilder:object:root=true
// +kubebuilder:subresource:status
type Searchpe struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`

	// +optional
	Spec SearchpeSpec `json:"spec,omitempty"`

	// +optional
	Status SearchpeStatus `json:"status,omitempty"`
}

// SearchpeList is a list of Searchpe.
// +kubebuilder:object:root=true
type SearchpeList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []Searchpe `json:"items"`
}
