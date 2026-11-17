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
	"strings"

	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	appsv1ac "k8s.io/client-go/applyconfigurations/apps/v1"
	corev1ac "k8s.io/client-go/applyconfigurations/core/v1"
	metav1ac "k8s.io/client-go/applyconfigurations/meta/v1"
	"k8s.io/utils/ptr"

	"github.com/project-openubl/searchpe/operator/api/v1alpha1"
)

const (
	certificatesDir = "/mnt/certificates"
	workspaceDir    = "/mnt/workspace"
)

func deployment(cr *v1alpha1.Searchpe, config Config) *appsv1ac.DeploymentApplyConfiguration {
	server := newServerConfig(cr)

	image := cr.Spec.Image
	if image == "" {
		image = config.Image
	}
	container := corev1ac.Container().
		WithName("searchpe").
		WithImage(image).
		WithEnv(server.env...).
		WithPorts(
			corev1ac.ContainerPort().WithName("http").WithProtocol(corev1.ProtocolTCP).WithContainerPort(httpPort),
			corev1ac.ContainerPort().WithName("https").WithProtocol(corev1.ProtocolTCP).WithContainerPort(httpsPort),
		).
		WithReadinessProbe(httpProbe("/q/health/ready", 250)).
		WithLivenessProbe(httpProbe("/q/health/live", 150)).
		WithImagePullPolicy(config.ImagePullPolicy).
		WithVolumeMounts(server.mounts...)

	pod := corev1ac.PodSpec().
		WithRestartPolicy(corev1.RestartPolicyAlways).
		WithTerminationGracePeriodSeconds(30).
		WithContainers(container).
		WithVolumes(server.volumes...)
	for _, secret := range cr.Spec.ImagePullSecrets {
		pod.WithImagePullSecrets(corev1ac.LocalObjectReference().WithName(secret.Name))
	}

	return appsv1ac.Deployment(deploymentName(cr), cr.Namespace).
		WithLabels(commonLabels(cr)).
		WithOwnerReferences(ownerReference(cr)).
		WithSpec(appsv1ac.DeploymentSpec().
			WithReplicas(ptr.Deref(cr.Spec.Instances, 1)).
			WithSelector(metav1ac.LabelSelector().WithMatchLabels(selectorLabels)).
			WithTemplate(corev1ac.PodTemplateSpec().
				WithLabels(podLabels(cr)).
				WithSpec(pod)))
}

func httpProbe(path string, failureThreshold int32) *corev1ac.ProbeApplyConfiguration {
	return corev1ac.Probe().
		WithHTTPGet(corev1ac.HTTPGetAction().WithPath(path).WithPort(intstr.FromString("http"))).
		WithInitialDelaySeconds(20).
		WithPeriodSeconds(2).
		WithFailureThreshold(failureThreshold)
}

// serverConfig is how the Searchpe server is configured: environment variables and volumes.
type serverConfig struct {
	env     []*corev1ac.EnvVarApplyConfiguration
	volumes []*corev1ac.VolumeApplyConfiguration
	mounts  []*corev1ac.VolumeMountApplyConfiguration
}

// newServerConfig translates the spec of cr into the configuration of the server. The order of
// the variables is the Java operator's: another order would restart the pods it created.
func newServerConfig(cr *v1alpha1.Searchpe) *serverConfig {
	c := &serverConfig{}
	profiles := []string{"prod"}

	for _, option := range cr.Spec.AdditionalOptions {
		env := corev1ac.EnvVar().WithName(option.Name)
		switch {
		case option.Secret != nil:
			env.WithValueFrom(secretKeyRef(option.Secret))
		case option.Value != "":
			env.WithValue(option.Value)
		}
		c.env = append(c.env, env)
	}

	if tlsEnabled(cr) {
		c.value("QUARKUS_HTTP_SSL_CERTIFICATE_FILE", certificatesDir+"/tls.crt")
		c.value("QUARKUS_HTTP_SSL_CERTIFICATE_KEY_FILE", certificatesDir+"/tls.key")
		c.value("QUARKUS_HTTP_INSECURE_REQUESTS", "redirect")
		c.volume(corev1ac.Volume().
			WithName("searchpe-tls-certificates").
			WithSecret(corev1ac.SecretVolumeSource().WithSecretName(cr.Spec.HTTP.TLSSecret).WithOptional(false)),
			certificatesDir)
	}

	if db := cr.Spec.Database; db != nil {
		c.secret("QUARKUS_DATASOURCE_USERNAME", db.UsernameSecret)
		c.secret("QUARKUS_DATASOURCE_PASSWORD", db.PasswordSecret)
		c.value("QUARKUS_DATASOURCE_JDBC_URL", db.URL)
	}

	if basicAuth := cr.Spec.BasicAuth; basicAuth != nil && basicAuth.Enabled {
		key := basicAuth.SessionEncryptionKeySecret
		if key == nil {
			key = &corev1.SecretKeySelector{
				LocalObjectReference: corev1.LocalObjectReference{Name: basicAuthSecretName(cr)},
				Key:                  basicAuthEncryptionKey,
				Optional:             ptr.To(false),
			}
		}
		c.secret("QUARKUS_HTTP_AUTH_SESSION_ENCRYPTION_KEY", key)
		profiles = append(profiles, "basic")
	}

	if oidc := cr.Spec.OIDC; oidc != nil && oidc.Enabled {
		c.value("QUARKUS_OIDC_AUTH_SERVER_URL", oidc.ServerURL)
		c.value("QUARKUS_OIDC_CLIENT_ID", oidc.ClientID)
		c.secret("QUARKUS_OIDC_CREDENTIALS_SECRET", oidc.CredentialsSecret)
		profiles = append(profiles, "oidc")
	}

	if sunat := cr.Spec.Sunat; sunat != nil {
		c.value("SEARCHPE_SUNAT_PADRONREDUCIDOURL", sunat.PadronReducidoURL)
		c.value("SEARCHPE_SCHEDULED_CRON", sunat.PadronReducidoCron)
	}

	c.value("SEARCHPE_WORKSPACE_DIRECTORY", workspaceDir)
	c.volume(corev1ac.Volume().WithName("searchpe-workspace").WithEmptyDir(corev1ac.EmptyDirVolumeSource()), workspaceDir)

	c.value("QUARKUS_PROFILE", strings.Join(profiles, ","))
	return c
}

// value adds the variable name, unless value is blank.
func (c *serverConfig) value(name, value string) {
	if strings.TrimSpace(value) != "" {
		c.env = append(c.env, corev1ac.EnvVar().WithName(name).WithValue(value))
	}
}

// secret adds the variable name from the key of a secret, unless ref is nil.
func (c *serverConfig) secret(name string, ref *corev1.SecretKeySelector) {
	if ref != nil {
		c.env = append(c.env, corev1ac.EnvVar().WithName(name).WithValueFrom(secretKeyRef(ref)))
	}
}

// volume adds the volume, mounted at mountPath.
func (c *serverConfig) volume(volume *corev1ac.VolumeApplyConfiguration, mountPath string) {
	c.volumes = append(c.volumes, volume)
	c.mounts = append(c.mounts, corev1ac.VolumeMount().WithName(*volume.Name).WithMountPath(mountPath))
}

func secretKeyRef(ref *corev1.SecretKeySelector) *corev1ac.EnvVarSourceApplyConfiguration {
	selector := corev1ac.SecretKeySelector().WithName(ref.Name).WithKey(ref.Key)
	if ref.Optional != nil {
		selector.WithOptional(*ref.Optional)
	}
	return corev1ac.EnvVarSource().WithSecretKeyRef(selector)
}
