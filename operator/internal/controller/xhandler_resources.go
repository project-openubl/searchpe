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
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/url"
	"slices"
	"strings"

	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	appsv1ac "k8s.io/client-go/applyconfigurations/apps/v1"
	batchv1ac "k8s.io/client-go/applyconfigurations/batch/v1"
	corev1ac "k8s.io/client-go/applyconfigurations/core/v1"
	metav1ac "k8s.io/client-go/applyconfigurations/meta/v1"
	networkingv1ac "k8s.io/client-go/applyconfigurations/networking/v1"
	policyv1ac "k8s.io/client-go/applyconfigurations/policy/v1"
	"k8s.io/utils/ptr"

	xhandlerv1alpha1 "github.com/project-openubl/searchpe/operator/api/xhandler/v1alpha1"
)

// The resources of an XHandler port xhandler/deploy/base: see its manifests for the reasons of
// each setting.
const (
	xhAppPort         = 8080
	xhEngineAddr      = "127.0.0.1:8090"
	xhEngineURL       = "http://localhost:8090"
	xhEngineBinary    = "/usr/local/bin/xhandler-engine"
	egressProxyPort   = 3128
	xhPostgresPort    = 5432
	xhDefaultReplicas = 2
	// Longer than a full SUNAT call (120 s) plus the app's shutdown timeout (135 s)
	xhTerminationGracePeriod = 150
	xhDefaultModuleMountPath = "/opt/hsm"

	componentAPI         = "api"
	componentWorker      = "worker"
	componentUI          = "ui"
	componentMigrate     = "migrate"
	componentEgressProxy = "egress-proxy"

	// The app.kubernetes.io/name of the pods. The api and worker pods share one, which the
	// network policies select.
	xhAppLabel         = "xhandler-service"
	xhUILabel          = "xhandler-ui"
	xhMigrateLabel     = "xhandler-migrate"
	xhEgressProxyLabel = "egress-proxy"
)

// The Squid configuration of the egress proxy: HTTPS CONNECT to port 443 of public addresses only.
const squidConfig = `http_port 3128
acl private dst 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 100.64.0.0/10 169.254.0.0/16 127.0.0.0/8 0.0.0.0/8 ::1 fc00::/7 fe80::/10
acl ssl_port port 443
acl CONNECT method CONNECT
http_access deny private
http_access deny !CONNECT
http_access deny CONNECT !ssl_port
http_access allow CONNECT ssl_port
http_access deny all
cache deny all
access_log stdio:/dev/stdout
cache_log stdio:/dev/stderr
pid_filename none
`

// The ranges the pods never reach directly: private, CGNAT, link-local (metadata) and loopback.
var privateRanges = []string{"10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10", "169.254.0.0/16", "127.0.0.0/8"}

// The namespaces whose pods reach the api and the UI when the spec names none.
var defaultIngressNamespaces = []string{"envoy-gateway-system", "monitoring"}

// xhImages are the images an XHandler runs.
type xhImages struct {
	app, engine, ui, egressProxy string
}

func xhName(cr *xhandlerv1alpha1.XHandler, suffix string) string { return cr.Name + "-" + suffix }

func xhAPIName(cr *xhandlerv1alpha1.XHandler) string         { return xhName(cr, componentAPI) }
func xhWorkerName(cr *xhandlerv1alpha1.XHandler) string      { return xhName(cr, componentWorker) }
func xhUIName(cr *xhandlerv1alpha1.XHandler) string          { return xhName(cr, componentUI) }
func xhEgressProxyName(cr *xhandlerv1alpha1.XHandler) string { return xhName(cr, componentEgressProxy) }
func xhIngressName(cr *xhandlerv1alpha1.XHandler) string     { return cr.Name }

// xhMigrateJobName is the name of the migration Job of image: a new image runs a new Job.
func xhMigrateJobName(cr *xhandlerv1alpha1.XHandler, image string) string {
	sum := sha256.Sum256([]byte(image))
	return xhName(cr, componentMigrate+"-"+hex.EncodeToString(sum[:])[:10])
}

// The names of the network policies.
func xhNetworkPolicyNames(cr *xhandlerv1alpha1.XHandler) []string {
	return []string{xhName(cr, "default-deny"), xhName(cr, "ingress"), xhName(cr, "egress"), xhName(cr, "migrate"), xhEgressProxyName(cr)}
}

// xhSelector selects the pods of a component of cr. Not the Searchpe labels: the selector of
// the Searchpe deployments selects every pod with them.
func xhSelector(cr *xhandlerv1alpha1.XHandler, name, component string) map[string]string {
	return map[string]string{
		"app.kubernetes.io/name":      name,
		"app.kubernetes.io/instance":  cr.Name,
		"app.kubernetes.io/component": component,
	}
}

// xhLabels are the labels of a resource of a component of cr, and of its pods.
func xhLabels(cr *xhandlerv1alpha1.XHandler, name, component string) map[string]string {
	labels := xhSelector(cr, name, component)
	labels["app.kubernetes.io/part-of"] = "xhandler-service"
	return labels
}

func xhOwnerReference(cr *xhandlerv1alpha1.XHandler) *metav1ac.OwnerReferenceApplyConfiguration {
	return metav1ac.OwnerReference().
		WithAPIVersion(xhandlerv1alpha1.GroupVersion.String()).
		WithKind("XHandler").
		WithName(cr.Name).
		WithUID(cr.UID).
		WithController(true).
		WithBlockOwnerDeletion(true)
}

func enabled(value *bool, byDefault bool) bool { return ptr.Deref(value, byDefault) }

func xhIngressEnabled(cr *xhandlerv1alpha1.XHandler) bool {
	return cr.Spec.Ingress == nil || enabled(cr.Spec.Ingress.Enabled, true)
}

func xhUIEnabled(cr *xhandlerv1alpha1.XHandler) bool {
	return cr.Spec.UI == nil || enabled(cr.Spec.UI.Enabled, true)
}

func xhEgressProxyEnabled(cr *xhandlerv1alpha1.XHandler) bool {
	return cr.Spec.EgressProxy != nil && enabled(cr.Spec.EgressProxy.Enabled, false)
}

func xhNetworkPoliciesEnabled(cr *xhandlerv1alpha1.XHandler) bool {
	return cr.Spec.NetworkPolicies != nil && enabled(cr.Spec.NetworkPolicies.Enabled, false)
}

func xhTLSEnabled(cr *xhandlerv1alpha1.XHandler) bool {
	return cr.Spec.TLS != nil && strings.TrimSpace(cr.Spec.TLS.Secret) != ""
}

func roleReplicas(role *xhandlerv1alpha1.RoleSpec) int32 {
	if role == nil {
		return xhDefaultReplicas
	}
	return ptr.Deref(role.Instances, xhDefaultReplicas)
}

func uiReplicas(cr *xhandlerv1alpha1.XHandler) int32 {
	if cr.Spec.UI == nil {
		return xhDefaultReplicas
	}
	return ptr.Deref(cr.Spec.UI.Instances, xhDefaultReplicas)
}

func egressProxyReplicas(cr *xhandlerv1alpha1.XHandler) int32 {
	return ptr.Deref(cr.Spec.EgressProxy.Instances, xhDefaultReplicas)
}

// xhPublicURL is the public URL of the API: the spec's, or the one of the hostname.
func xhPublicURL(cr *xhandlerv1alpha1.XHandler) string {
	switch {
	case cr.Spec.PublicURL != "":
		return cr.Spec.PublicURL
	case cr.Spec.Hostname == "":
		return ""
	case xhTLSEnabled(cr):
		return "https://" + cr.Spec.Hostname
	default:
		return "http://" + cr.Spec.Hostname
	}
}

// xhEgressProxyAddress is the host:port of the egress proxy of cr.
func xhEgressProxyAddress(cr *xhandlerv1alpha1.XHandler) string {
	return fmt.Sprintf("%s:%d", xhEgressProxyName(cr), egressProxyPort)
}

// xhAPIURL is the URL of the api Service of cr, in the cluster.
func xhAPIURL(cr *xhandlerv1alpha1.XHandler) string {
	return fmt.Sprintf("http://%s:%d", xhAPIName(cr), xhAppPort)
}

func restrictedSecurityContext() *corev1ac.SecurityContextApplyConfiguration {
	return corev1ac.SecurityContext().
		WithAllowPrivilegeEscalation(false).
		WithReadOnlyRootFilesystem(true).
		WithCapabilities(corev1ac.Capabilities().WithDrop("ALL"))
}

func restrictedPodSecurityContext() *corev1ac.PodSecurityContextApplyConfiguration {
	return corev1ac.PodSecurityContext().
		WithRunAsNonRoot(true).
		WithSeccompProfile(corev1ac.SeccompProfile().WithType(corev1.SeccompProfileTypeRuntimeDefault))
}

func resources(cpuRequest, memoryRequest, memoryLimit string) *corev1ac.ResourceRequirementsApplyConfiguration {
	return corev1ac.ResourceRequirements().
		WithRequests(corev1.ResourceList{
			corev1.ResourceCPU:    resource.MustParse(cpuRequest),
			corev1.ResourceMemory: resource.MustParse(memoryRequest),
		}).
		WithLimits(corev1.ResourceList{corev1.ResourceMemory: resource.MustParse(memoryLimit)})
}

// spreadConstraints spread the pods evenly across zones (a lost zone's tainted nodes don't count)
// and, if possible, across nodes.
func spreadConstraints(selector map[string]string) []*corev1ac.TopologySpreadConstraintApplyConfiguration {
	return []*corev1ac.TopologySpreadConstraintApplyConfiguration{
		corev1ac.TopologySpreadConstraint().
			WithMaxSkew(1).
			WithTopologyKey("topology.kubernetes.io/zone").
			WithWhenUnsatisfiable(corev1.DoNotSchedule).
			WithNodeTaintsPolicy(corev1.NodeInclusionPolicyHonor).
			WithLabelSelector(metav1ac.LabelSelector().WithMatchLabels(selector)),
		corev1ac.TopologySpreadConstraint().
			WithMaxSkew(1).
			WithTopologyKey("kubernetes.io/hostname").
			WithWhenUnsatisfiable(corev1.ScheduleAnyway).
			WithLabelSelector(metav1ac.LabelSelector().WithMatchLabels(selector)),
	}
}

func emptyDirVolume(name string) *corev1ac.VolumeApplyConfiguration {
	return corev1ac.Volume().WithName(name).WithEmptyDir(corev1ac.EmptyDirVolumeSource())
}

// rollingDeployment is a deployment of the pods of component that replaces them one at a time,
// never below replicas.
func rollingDeployment(cr *xhandlerv1alpha1.XHandler, name, appName, component string, replicas int32,
	pod *corev1ac.PodSpecApplyConfiguration) *appsv1ac.DeploymentApplyConfiguration {
	for _, secret := range cr.Spec.ImagePullSecrets {
		pod.WithImagePullSecrets(corev1ac.LocalObjectReference().WithName(secret.Name))
	}
	return appsv1ac.Deployment(name, cr.Namespace).
		WithLabels(xhLabels(cr, appName, component)).
		WithOwnerReferences(xhOwnerReference(cr)).
		WithSpec(appsv1ac.DeploymentSpec().
			WithReplicas(replicas).
			WithSelector(metav1ac.LabelSelector().WithMatchLabels(xhSelector(cr, appName, component))).
			WithStrategy(appsv1ac.DeploymentStrategy().
				WithRollingUpdate(appsv1ac.RollingUpdateDeployment().
					WithMaxUnavailable(intstr.FromInt32(0)).
					WithMaxSurge(intstr.FromInt32(1)))).
			WithTemplate(corev1ac.PodTemplateSpec().
				WithLabels(xhLabels(cr, appName, component)).
				WithSpec(pod)))
}

// roleDeployment is the deployment of a role (api or worker): the app, with the engine as a
// native sidecar, which starts before the app and stops after it.
func roleDeployment(cr *xhandlerv1alpha1.XHandler, images xhImages, pullPolicy corev1.PullPolicy, component string) (*appsv1ac.DeploymentApplyConfiguration, error) {
	app, err := appConfig(cr, component)
	if err != nil {
		return nil, err
	}
	container := corev1ac.Container().
		WithName("app").
		WithImage(images.app).
		WithImagePullPolicy(pullPolicy).
		WithPorts(corev1ac.ContainerPort().WithName("http").WithProtocol(corev1.ProtocolTCP).WithContainerPort(xhAppPort)).
		WithEnv(app.env...).
		WithReadinessProbe(corev1ac.Probe().
			WithHTTPGet(corev1ac.HTTPGetAction().WithPath("/q/health/ready").WithPort(intstr.FromString("http"))).
			WithTimeoutSeconds(3)).
		WithLivenessProbe(corev1ac.Probe().
			WithHTTPGet(corev1ac.HTTPGetAction().WithPath("/q/health/live").WithPort(intstr.FromString("http"))).
			WithTimeoutSeconds(3)).
		WithResources(resources("250m", "512Mi", "1Gi")).
		WithSecurityContext(restrictedSecurityContext()).
		WithVolumeMounts(app.mounts...)

	pod := corev1ac.PodSpec().
		WithAutomountServiceAccountToken(false).
		WithTerminationGracePeriodSeconds(xhTerminationGracePeriod).
		WithTopologySpreadConstraints(spreadConstraints(xhSelector(cr, xhAppLabel, component))...).
		WithSecurityContext(restrictedPodSecurityContext()).
		WithInitContainers(engineContainer(cr, images.engine, pullPolicy)).
		WithContainers(container).
		WithVolumes(app.volumes...)

	name := xhAPIName(cr)
	replicas := roleReplicas(cr.Spec.API)
	if component == componentWorker {
		name = xhWorkerName(cr)
		replicas = roleReplicas(cr.Spec.Worker)
	}
	return rollingDeployment(cr, name, xhAppLabel, component, replicas, pod), nil
}

// engineContainer is the engine sidecar. It listens on localhost only, which kubelet HTTP probes
// can't reach: it is probed with its binary.
func engineContainer(cr *xhandlerv1alpha1.XHandler, image string, pullPolicy corev1.PullPolicy) *corev1ac.ContainerApplyConfiguration {
	c := &serverConfig{}
	c.value("XHANDLER_ENGINE_ADDR", xhEngineAddr)
	c.value("XHANDLER_SUNAT_FAKE_URL", cr.Spec.SunatFakeURL)
	if xhEgressProxyEnabled(cr) {
		c.value("HTTPS_PROXY", "http://"+xhEgressProxyAddress(cr))
		noProxy := []string{"localhost", "127.0.0.1", ".svc", ".cluster.local"}
		// The SUNAT fake runs in the cluster: the proxy denies private addresses
		if fake, err := url.Parse(cr.Spec.SunatFakeURL); err == nil && fake.Hostname() != "" && !slices.Contains(noProxy, fake.Hostname()) {
			noProxy = append(noProxy, fake.Hostname())
		}
		c.value("NO_PROXY", strings.Join(noProxy, ","))
	}

	health := func() *corev1ac.ProbeApplyConfiguration {
		return corev1ac.Probe().
			WithExec(corev1ac.ExecAction().WithCommand(xhEngineBinary, "--health")).
			WithTimeoutSeconds(3)
	}
	return corev1ac.Container().
		WithName("engine").
		WithImage(image).
		WithImagePullPolicy(pullPolicy).
		WithRestartPolicy(corev1.ContainerRestartPolicyAlways).
		WithEnv(c.env...).
		WithStartupProbe(health().WithPeriodSeconds(1).WithFailureThreshold(30)).
		WithReadinessProbe(health()).
		WithLivenessProbe(health()).
		WithResources(resources("250m", "64Mi", "256Mi")).
		WithSecurityContext(restrictedSecurityContext())
}

// appConfig translates the spec of cr into the configuration of the app of a role: environment
// variables (see xhandler/app/src/main/resources/application.properties) and volumes.
func appConfig(cr *xhandlerv1alpha1.XHandler, component string) (*serverConfig, error) {
	c := &serverConfig{}
	c.value("XHANDLER_ROLE", component)
	c.value("XHANDLER_ENGINE_URL", xhEngineURL)
	// The app logs in as a non-owner member of xhandler_app: the migration Job migrates
	c.value("QUARKUS_FLYWAY_MIGRATE_AT_START", "false")

	if db := cr.Spec.Database; db != nil {
		c.value("QUARKUS_DATASOURCE_JDBC_URL", db.URL)
		c.secret("QUARKUS_DATASOURCE_USERNAME", db.UsernameSecret)
		c.secret("QUARKUS_DATASOURCE_PASSWORD", db.PasswordSecret)
	}

	if oidc := cr.Spec.OIDC; oidc != nil {
		c.value("QUARKUS_OIDC_AUTH_SERVER_URL", oidc.ServerURL)
		c.value("QUARKUS_OIDC_CLIENT_ID", oidc.ClientID)
		c.secret("QUARKUS_OIDC_CREDENTIALS_SECRET", oidc.CredentialsSecret)
	}

	if vault := cr.Spec.Vault; vault != nil {
		c.value("XHANDLER_VAULT_PKCS11_LIBRARY", vault.PKCS11Library)
		c.secret("XHANDLER_VAULT_PKCS11_PIN", vault.PKCS11PinSecret)
		if vault.ModuleVolume != nil {
			volume, err := volumeApplyConfiguration("hsm-module", vault.ModuleVolume)
			if err != nil {
				return nil, err
			}
			mountPath := vault.ModuleMountPath
			if mountPath == "" {
				mountPath = xhDefaultModuleMountPath
			}
			c.volumes = append(c.volumes, volume)
			c.mounts = append(c.mounts, corev1ac.VolumeMount().WithName("hsm-module").WithMountPath(mountPath).WithReadOnly(true))
		}
	}

	if storage := cr.Spec.Storage; storage != nil {
		c.value("XHANDLER_STORAGE_S3_ENDPOINT", storage.S3Endpoint)
		c.value("XHANDLER_STORAGE_S3_BUCKET", storage.Bucket)
		c.value("XHANDLER_STORAGE_S3_REGION", storage.Region)
		c.secret("XHANDLER_STORAGE_S3_ACCESS_KEY", storage.AccessKeySecret)
		c.secret("XHANDLER_STORAGE_S3_SECRET_KEY", storage.SecretKeySecret)
	}

	if ose := cr.Spec.OSE; ose != nil {
		c.value("XHANDLER_OSE_RUC", ose.RUC)
		c.value("XHANDLER_OSE_ENVIRONMENT", ose.Environment)
		c.value("XHANDLER_OSE_FORWARD_URL", ose.ForwardURL)
	}

	c.value("XHANDLER_PUBLIC_URL", xhPublicURL(cr))
	c.value("XHANDLER_SUNAT_FAKE_URL", cr.Spec.SunatFakeURL)
	if xhEgressProxyEnabled(cr) {
		// Webhooks to tenant-supplied hosts go through the proxy, which pins DNS
		c.value("XHANDLER_EGRESS_PROXY", xhEgressProxyAddress(cr))
	}

	c.volumes = append(c.volumes, emptyDirVolume("tmp"))
	c.mounts = append(c.mounts, corev1ac.VolumeMount().WithName("tmp").WithMountPath("/tmp"))

	c.env = withAdditionalOptions(c.env, cr.Spec.AdditionalOptions)
	return c, nil
}

// withAdditionalOptions adds the options to env. An option replaces the variable of the same
// name: server-side apply refuses two variables with one name.
func withAdditionalOptions(env []*corev1ac.EnvVarApplyConfiguration, options []xhandlerv1alpha1.ValueOrSecret) []*corev1ac.EnvVarApplyConfiguration {
	for _, option := range options {
		if option.Name == "" {
			continue
		}
		variable := corev1ac.EnvVar().WithName(option.Name)
		switch {
		case option.Secret != nil:
			variable.WithValueFrom(secretKeyRef(option.Secret))
		default:
			variable.WithValue(option.Value)
		}
		index := slices.IndexFunc(env, func(e *corev1ac.EnvVarApplyConfiguration) bool { return *e.Name == option.Name })
		if index >= 0 {
			env[index] = variable
		} else {
			env = append(env, variable)
		}
	}
	return env
}

// volumeApplyConfiguration is the volume name with source. Apply configurations are the JSON of
// their objects.
func volumeApplyConfiguration(name string, source *corev1.VolumeSource) (*corev1ac.VolumeApplyConfiguration, error) {
	data, err := json.Marshal(corev1.Volume{Name: name, VolumeSource: *source})
	if err != nil {
		return nil, err
	}
	volume := &corev1ac.VolumeApplyConfiguration{}
	if err := json.Unmarshal(data, volume); err != nil {
		return nil, err
	}
	return volume, nil
}

// migrateJob runs the database migrations of image, as the schema owner, and exits before the
// application starts: no workers, no HTTP server, no role check.
func migrateJob(cr *xhandlerv1alpha1.XHandler, image string, pullPolicy corev1.PullPolicy) *batchv1ac.JobApplyConfiguration {
	c := &serverConfig{}
	c.value("QUARKUS_FLYWAY_MIGRATE_AT_START", "true")
	c.value("QUARKUS_INIT_AND_EXIT", "true")
	c.value("XHANDLER_ROLE", componentMigrate)
	if oidc := cr.Spec.OIDC; oidc != nil {
		c.value("QUARKUS_OIDC_AUTH_SERVER_URL", oidc.ServerURL)
	}
	if db := cr.Spec.Database; db != nil {
		c.value("QUARKUS_DATASOURCE_JDBC_URL", db.URL)
		c.secret("QUARKUS_DATASOURCE_USERNAME", db.OwnerUsernameSecret)
		c.secret("QUARKUS_DATASOURCE_PASSWORD", db.OwnerPasswordSecret)
	}

	pod := corev1ac.PodSpec().
		WithRestartPolicy(corev1.RestartPolicyNever).
		WithAutomountServiceAccountToken(false).
		WithSecurityContext(restrictedPodSecurityContext()).
		WithContainers(corev1ac.Container().
			WithName("migrate").
			WithImage(image).
			WithImagePullPolicy(pullPolicy).
			WithEnv(c.env...).
			WithResources(resources("250m", "512Mi", "1Gi")).
			WithSecurityContext(restrictedSecurityContext()).
			WithVolumeMounts(corev1ac.VolumeMount().WithName("tmp").WithMountPath("/tmp"))).
		WithVolumes(emptyDirVolume("tmp"))
	for _, secret := range cr.Spec.ImagePullSecrets {
		pod.WithImagePullSecrets(corev1ac.LocalObjectReference().WithName(secret.Name))
	}

	return batchv1ac.Job(xhMigrateJobName(cr, image), cr.Namespace).
		WithLabels(xhLabels(cr, xhMigrateLabel, componentMigrate)).
		WithOwnerReferences(xhOwnerReference(cr)).
		WithSpec(batchv1ac.JobSpec().
			WithBackoffLimit(2).
			WithActiveDeadlineSeconds(1800).
			WithTemplate(corev1ac.PodTemplateSpec().
				WithLabels(xhLabels(cr, xhMigrateLabel, componentMigrate)).
				WithSpec(pod)))
}

// uiDeployment is the deployment of the UI. searchpeURL is the URL of the Searchpe it looks RUCs
// and DNIs up in, empty without one.
func uiDeployment(cr *xhandlerv1alpha1.XHandler, image string, pullPolicy corev1.PullPolicy, searchpeURL string) *appsv1ac.DeploymentApplyConfiguration {
	c := &serverConfig{}
	c.value("XHANDLER_API_URL", xhAPIURL(cr))
	c.value("SEARCHPE_API_URL", searchpeURL)
	if oidc := cr.Spec.OIDC; oidc != nil {
		c.value("OIDC_SERVER_URL", oidc.ServerURL)
		c.value("OIDC_CLIENT_ID", oidc.ClientID)
	}

	tcpProbe := func() *corev1ac.ProbeApplyConfiguration {
		return corev1ac.Probe().WithTCPSocket(corev1ac.TCPSocketAction().WithPort(intstr.FromString("http"))).WithTimeoutSeconds(3)
	}
	pod := corev1ac.PodSpec().
		WithAutomountServiceAccountToken(false).
		WithTopologySpreadConstraints(spreadConstraints(xhSelector(cr, xhUILabel, componentUI))...).
		WithSecurityContext(restrictedPodSecurityContext()).
		WithContainers(corev1ac.Container().
			WithName("ui").
			WithImage(image).
			WithImagePullPolicy(pullPolicy).
			WithPorts(corev1ac.ContainerPort().WithName("http").WithProtocol(corev1.ProtocolTCP).WithContainerPort(xhAppPort)).
			WithEnv(c.env...).
			WithReadinessProbe(tcpProbe()).
			WithLivenessProbe(tcpProbe()).
			WithResources(resources("50m", "64Mi", "256Mi")).
			WithSecurityContext(restrictedSecurityContext()).
			WithVolumeMounts(corev1ac.VolumeMount().WithName("tmp").WithMountPath("/tmp"))).
		WithVolumes(emptyDirVolume("tmp"))
	return rollingDeployment(cr, xhUIName(cr), xhUILabel, componentUI, uiReplicas(cr), pod)
}

// xhService is the Service of the pods of component, on port port (named http or proxy).
func xhService(cr *xhandlerv1alpha1.XHandler, name, appName, component, portName string, port int32) *corev1ac.ServiceApplyConfiguration {
	return corev1ac.Service(name, cr.Namespace).
		WithLabels(xhLabels(cr, appName, component)).
		WithOwnerReferences(xhOwnerReference(cr)).
		WithSpec(corev1ac.ServiceSpec().
			WithType(corev1.ServiceTypeClusterIP).
			WithSelector(xhSelector(cr, appName, component)).
			WithPorts(corev1ac.ServicePort().
				WithName(portName).
				WithPort(port).
				WithTargetPort(intstr.FromString(portName)).
				WithProtocol(corev1.ProtocolTCP)))
}

// podDisruptionBudget lets voluntary disruptions (node drains, upgrades) take one pod of
// component at a time.
func podDisruptionBudget(cr *xhandlerv1alpha1.XHandler, name, appName, component string) *policyv1ac.PodDisruptionBudgetApplyConfiguration {
	return policyv1ac.PodDisruptionBudget(name, cr.Namespace).
		WithLabels(xhLabels(cr, appName, component)).
		WithOwnerReferences(xhOwnerReference(cr)).
		WithSpec(policyv1ac.PodDisruptionBudgetSpec().
			WithMaxUnavailable(intstr.FromInt32(1)).
			WithSelector(metav1ac.LabelSelector().WithMatchLabels(xhSelector(cr, appName, component))))
}

// xhIngress exposes the UI, or the api when the UI is disabled. With the UI, what clients other
// than browsers call (the REST and SOAP APIs, the buyers' links, the API reference) still goes
// to the api.
func xhIngress(cr *xhandlerv1alpha1.XHandler) *networkingv1ac.IngressApplyConfiguration {
	path := func(prefix, service string) *networkingv1ac.HTTPIngressPathApplyConfiguration {
		return networkingv1ac.HTTPIngressPath().
			WithPath(prefix).
			WithPathType(networkingv1.PathTypePrefix).
			WithBackend(networkingv1ac.IngressBackend().
				WithService(networkingv1ac.IngressServiceBackend().
					WithName(service).
					WithPort(networkingv1ac.ServiceBackendPort().WithNumber(xhAppPort))))
	}
	var paths []*networkingv1ac.HTTPIngressPathApplyConfiguration
	if xhUIEnabled(cr) {
		for _, prefix := range []string{"/api", "/ose", "/ol-ti-itcpfegem", "/q/openapi", "/q/swagger-ui"} {
			paths = append(paths, path(prefix, xhAPIName(cr)))
		}
		paths = append(paths, path("/", xhUIName(cr)))
	} else {
		paths = append(paths, path("/", xhAPIName(cr)))
	}

	rule := networkingv1ac.IngressRule().WithHTTP(networkingv1ac.HTTPIngressRuleValue().WithPaths(paths...))
	if cr.Spec.Hostname != "" {
		rule.WithHost(cr.Spec.Hostname)
	}
	spec := networkingv1ac.IngressSpec().WithRules(rule)
	if xhTLSEnabled(cr) {
		tls := networkingv1ac.IngressTLS().WithSecretName(cr.Spec.TLS.Secret)
		if cr.Spec.Hostname != "" {
			tls.WithHosts(cr.Spec.Hostname)
		}
		spec.WithTLS(tls)
	}

	component := componentAPI
	appName := xhAppLabel
	if xhUIEnabled(cr) {
		component, appName = componentUI, xhUILabel
	}
	return networkingv1ac.Ingress(xhIngressName(cr), cr.Namespace).
		WithLabels(xhLabels(cr, appName, component)).
		// The OSE's SOAP intake carries ZIPs of up to 10 MB in base64
		WithAnnotations(map[string]string{"nginx.ingress.kubernetes.io/proxy-body-size": "16m"}).
		WithOwnerReferences(xhOwnerReference(cr)).
		WithSpec(spec)
}

func egressProxyConfigMap(cr *xhandlerv1alpha1.XHandler) *corev1ac.ConfigMapApplyConfiguration {
	return corev1ac.ConfigMap(xhEgressProxyName(cr), cr.Namespace).
		WithLabels(xhLabels(cr, xhEgressProxyLabel, componentEgressProxy)).
		WithOwnerReferences(xhOwnerReference(cr)).
		WithData(map[string]string{"squid.conf": squidConfig})
}

// egressProxyDeployment runs Squid in the foreground (the image's entrypoint is a service manager).
func egressProxyDeployment(cr *xhandlerv1alpha1.XHandler, image string, pullPolicy corev1.PullPolicy) *appsv1ac.DeploymentApplyConfiguration {
	selector := xhSelector(cr, xhEgressProxyLabel, componentEgressProxy)
	pod := corev1ac.PodSpec().
		WithAutomountServiceAccountToken(false).
		WithTopologySpreadConstraints(spreadConstraints(selector)...).
		WithSecurityContext(restrictedPodSecurityContext().WithRunAsUser(31)).
		WithContainers(corev1ac.Container().
			WithName("squid").
			WithImage(image).
			WithImagePullPolicy(pullPolicy).
			WithCommand("/usr/sbin/squid").
			WithArgs("-N", "-f", "/etc/squid/squid.conf").
			WithPorts(corev1ac.ContainerPort().WithName("proxy").WithProtocol(corev1.ProtocolTCP).WithContainerPort(egressProxyPort)).
			WithReadinessProbe(corev1ac.Probe().WithTCPSocket(corev1ac.TCPSocketAction().WithPort(intstr.FromString("proxy")))).
			WithResources(resources("100m", "64Mi", "256Mi")).
			WithSecurityContext(restrictedSecurityContext()).
			WithVolumeMounts(
				corev1ac.VolumeMount().WithName("config").WithMountPath("/etc/squid"),
				corev1ac.VolumeMount().WithName("tmp").WithMountPath("/var/spool/squid"),
				corev1ac.VolumeMount().WithName("run").WithMountPath("/var/run/squid"))).
		WithVolumes(
			corev1ac.Volume().WithName("config").WithConfigMap(corev1ac.ConfigMapVolumeSource().WithName(xhEgressProxyName(cr))),
			emptyDirVolume("tmp"),
			emptyDirVolume("run"))
	return rollingDeployment(cr, xhEgressProxyName(cr), xhEgressProxyLabel, componentEgressProxy, egressProxyReplicas(cr), pod)
}

// networkPolicies deny the xhandler pods everything they don't need. Tenant-supplied hosts are
// reached on public addresses only; SUNAT and third-party OSEs through the egress proxy.
func networkPolicies(cr *xhandlerv1alpha1.XHandler) []*networkingv1ac.NetworkPolicyApplyConfiguration {
	spec := cr.Spec.NetworkPolicies
	appPods := metav1ac.LabelSelector().WithMatchLabels(map[string]string{
		"app.kubernetes.io/name":     xhAppLabel,
		"app.kubernetes.io/instance": cr.Name,
	})
	policy := func(name, appName string, pods *metav1ac.LabelSelectorApplyConfiguration,
		types ...networkingv1.PolicyType) *networkingv1ac.NetworkPolicyApplyConfiguration {
		return networkingv1ac.NetworkPolicy(name, cr.Namespace).
			WithLabels(xhLabels(cr, appName, "network-policy")).
			WithOwnerReferences(xhOwnerReference(cr)).
			WithSpec(networkingv1ac.NetworkPolicySpec().WithPodSelector(pods).WithPolicyTypes(types...))
	}

	ingressNamespaces := spec.IngressNamespaces
	if len(ingressNamespaces) == 0 {
		ingressNamespaces = defaultIngressNamespaces
	}
	ingress := networkingv1ac.NetworkPolicyIngressRule().
		WithFrom(networkingv1ac.NetworkPolicyPeer().
			WithNamespaceSelector(metav1ac.LabelSelector().
				WithMatchExpressions(metav1ac.LabelSelectorRequirement().
					WithKey("kubernetes.io/metadata.name").
					WithOperator(metav1.LabelSelectorOpIn).
					WithValues(ingressNamespaces...)))).
		WithPorts(tcpPort(xhAppPort))
	if xhUIEnabled(cr) {
		ingress.WithFrom(networkingv1ac.NetworkPolicyPeer().
			WithPodSelector(metav1ac.LabelSelector().WithMatchLabels(xhSelector(cr, xhUILabel, componentUI))))
	}

	egress := []*networkingv1ac.NetworkPolicyEgressRuleApplyConfiguration{
		dnsRule(),
		databaseRule(spec.DatabasePodSelector),
		networkingv1ac.NetworkPolicyEgressRule().
			WithTo(networkingv1ac.NetworkPolicyPeer().
				WithPodSelector(metav1ac.LabelSelector().WithMatchLabels(map[string]string{"app.kubernetes.io/name": "otel-collector"}))).
			WithPorts(tcpPort(4317), tcpPort(4318)),
	}
	if xhEgressProxyEnabled(cr) {
		egress = append(egress, networkingv1ac.NetworkPolicyEgressRule().
			WithTo(networkingv1ac.NetworkPolicyPeer().
				WithPodSelector(metav1ac.LabelSelector().WithMatchLabels(xhSelector(cr, xhEgressProxyLabel, componentEgressProxy)))).
			WithPorts(tcpPort(egressProxyPort)))
	}
	// Keycloak, S3, webhooks (443), tenant SMTP (25, 465, 587, 2525)
	egress = append(egress, networkingv1ac.NetworkPolicyEgressRule().
		WithTo(publicAddresses()).
		WithPorts(tcpPort(443), tcpPort(25), tcpPort(465), tcpPort(587), tcpPort(2525)))

	names := xhNetworkPolicyNames(cr)
	policies := []*networkingv1ac.NetworkPolicyApplyConfiguration{
		policy(names[0], xhAppLabel, appPods, networkingv1.PolicyTypeIngress, networkingv1.PolicyTypeEgress),
		policy(names[1], xhAppLabel, appPods, networkingv1.PolicyTypeIngress),
		policy(names[2], xhAppLabel, appPods, networkingv1.PolicyTypeEgress),
		policy(names[3], xhMigrateLabel,
			metav1ac.LabelSelector().WithMatchLabels(xhSelector(cr, xhMigrateLabel, componentMigrate)),
			networkingv1.PolicyTypeIngress, networkingv1.PolicyTypeEgress),
	}
	policies[1].Spec.WithIngress(ingress)
	policies[2].Spec.WithEgress(egress...)
	policies[3].Spec.WithEgress(dnsRule(), databaseRule(spec.DatabasePodSelector))

	if xhEgressProxyEnabled(cr) {
		proxy := policy(names[4], xhEgressProxyLabel,
			metav1ac.LabelSelector().WithMatchLabels(xhSelector(cr, xhEgressProxyLabel, componentEgressProxy)),
			networkingv1.PolicyTypeIngress, networkingv1.PolicyTypeEgress)
		proxy.Spec.
			WithIngress(networkingv1ac.NetworkPolicyIngressRule().
				WithFrom(networkingv1ac.NetworkPolicyPeer().WithPodSelector(appPods)).
				WithPorts(tcpPort(egressProxyPort))).
			WithEgress(dnsRule(), networkingv1ac.NetworkPolicyEgressRule().WithTo(publicAddresses()).WithPorts(tcpPort(443)))
		policies = append(policies, proxy)
	}
	return policies
}

func tcpPort(port int32) *networkingv1ac.NetworkPolicyPortApplyConfiguration {
	return networkingv1ac.NetworkPolicyPort().WithPort(intstr.FromInt32(port)).WithProtocol(corev1.ProtocolTCP)
}

// dnsRule reaches the DNS of the cluster, and NodeLocal DNSCache.
func dnsRule() *networkingv1ac.NetworkPolicyEgressRuleApplyConfiguration {
	return networkingv1ac.NetworkPolicyEgressRule().
		WithTo(
			networkingv1ac.NetworkPolicyPeer().
				WithNamespaceSelector(metav1ac.LabelSelector().WithMatchLabels(map[string]string{"kubernetes.io/metadata.name": "kube-system"})),
			networkingv1ac.NetworkPolicyPeer().WithIPBlock(networkingv1ac.IPBlock().WithCIDR("169.254.20.10/32"))).
		WithPorts(
			networkingv1ac.NetworkPolicyPort().WithPort(intstr.FromInt32(53)).WithProtocol(corev1.ProtocolUDP),
			tcpPort(53))
}

// databaseRule reaches PostgreSQL: the pods of selector, or any address without one.
func databaseRule(selector *metav1.LabelSelector) *networkingv1ac.NetworkPolicyEgressRuleApplyConfiguration {
	rule := networkingv1ac.NetworkPolicyEgressRule().WithPorts(tcpPort(xhPostgresPort))
	if selector != nil {
		rule.WithTo(networkingv1ac.NetworkPolicyPeer().WithPodSelector(labelSelector(selector)))
	}
	return rule
}

func publicAddresses() *networkingv1ac.NetworkPolicyPeerApplyConfiguration {
	return networkingv1ac.NetworkPolicyPeer().
		WithIPBlock(networkingv1ac.IPBlock().WithCIDR("0.0.0.0/0").WithExcept(privateRanges...))
}

func labelSelector(selector *metav1.LabelSelector) *metav1ac.LabelSelectorApplyConfiguration {
	ac := metav1ac.LabelSelector().WithMatchLabels(selector.MatchLabels)
	for _, requirement := range selector.MatchExpressions {
		ac.WithMatchExpressions(metav1ac.LabelSelectorRequirement().
			WithKey(requirement.Key).
			WithOperator(requirement.Operator).
			WithValues(requirement.Values...))
	}
	return ac
}
