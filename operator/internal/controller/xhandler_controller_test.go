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
	"context"
	"slices"
	"strings"
	"testing"

	appsv1 "k8s.io/api/apps/v1"
	batchv1 "k8s.io/api/batch/v1"
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	policyv1 "k8s.io/api/policy/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/api/meta"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/utils/ptr"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/config"
	metricsserver "sigs.k8s.io/controller-runtime/pkg/metrics/server"

	"github.com/project-openubl/searchpe/operator/api/v1alpha1"
	xhandlerv1alpha1 "github.com/project-openubl/searchpe/operator/api/xhandler/v1alpha1"
)

const (
	testXHandlerImage    = "ghcr.io/project-openubl/searchpe/xhandler-service:nightly"
	testEngineImage      = "ghcr.io/project-openubl/searchpe/xhandler-engine:nightly"
	testUIImage          = "ghcr.io/project-openubl/searchpe/xhandler-ui:nightly"
	testEgressProxyImage = "docker.io/ubuntu/squid:6.6-24.04_edge"
)

func newXHandlerReconciler() *XHandlerReconciler {
	return &XHandlerReconciler{
		Client: k8sClient,
		Config: XHandlerConfig{
			Image:            testXHandlerImage,
			EngineImage:      testEngineImage,
			UIImage:          testUIImage,
			EgressProxyImage: testEgressProxyImage,
			ImagePullPolicy:  corev1.PullAlways,
		},
	}
}

func newXHandler(t *testing.T, spec xhandlerv1alpha1.XHandlerSpec) *xhandlerv1alpha1.XHandler {
	t.Helper()
	cr := &xhandlerv1alpha1.XHandler{
		ObjectMeta: metav1.ObjectMeta{Name: "xh", Namespace: newNamespace(t)},
		Spec:       spec,
	}
	if err := k8sClient.Create(t.Context(), cr); err != nil {
		t.Fatal(err)
	}
	return cr
}

// reconcileXHandler reconciles cr with r (newXHandlerReconciler() when nil), and reads it again.
func reconcileXHandler(t *testing.T, r *XHandlerReconciler, cr *xhandlerv1alpha1.XHandler) ctrl.Result {
	t.Helper()
	if r == nil {
		r = newXHandlerReconciler()
	}
	result, err := r.Reconcile(t.Context(), ctrl.Request{NamespacedName: client.ObjectKeyFromObject(cr)})
	if err != nil {
		t.Fatalf("reconcile: %v", err)
	}
	if err := k8sClient.Get(t.Context(), client.ObjectKeyFromObject(cr), cr); err != nil {
		t.Fatal(err)
	}
	return result
}

func update(t *testing.T, cr *xhandlerv1alpha1.XHandler, change func(*xhandlerv1alpha1.XHandlerSpec)) {
	t.Helper()
	change(&cr.Spec)
	if err := k8sClient.Update(t.Context(), cr); err != nil {
		t.Fatal(err)
	}
}

// finishJob marks the migration Job of image finished, as the Job controller (which envtest
// lacks) would.
func finishJob(t *testing.T, cr *xhandlerv1alpha1.XHandler, image string, succeeded bool) {
	t.Helper()
	job := &batchv1.Job{}
	get(t.Context(), t, cr.Namespace, xhMigrateJobName(cr, image), job)
	now := metav1.Now()
	job.Status.StartTime = &now
	if succeeded {
		job.Status.CompletionTime = &now
		job.Status.Succeeded = 1
		job.Status.Conditions = []batchv1.JobCondition{
			{Type: batchv1.JobSuccessCriteriaMet, Status: corev1.ConditionTrue, LastTransitionTime: now},
			{Type: batchv1.JobComplete, Status: corev1.ConditionTrue, LastTransitionTime: now},
		}
	} else {
		job.Status.Failed = 3
		job.Status.Conditions = []batchv1.JobCondition{
			{Type: batchv1.JobFailureTarget, Status: corev1.ConditionTrue, LastTransitionTime: now},
			{Type: batchv1.JobFailed, Status: corev1.ConditionTrue, LastTransitionTime: now},
		}
	}
	if err := k8sClient.Status().Update(t.Context(), job); err != nil {
		t.Fatal(err)
	}
}

// makeAvailable marks a deployment available, as the Deployment controller would.
func makeAvailable(t *testing.T, namespace, name string) {
	t.Helper()
	deployment := &appsv1.Deployment{}
	get(t.Context(), t, namespace, name, deployment)
	replicas := ptr.Deref(deployment.Spec.Replicas, 1)
	deployment.Status = appsv1.DeploymentStatus{
		ObservedGeneration: deployment.Generation,
		Replicas:           replicas,
		UpdatedReplicas:    replicas,
		ReadyReplicas:      replicas,
		AvailableReplicas:  replicas,
	}
	if err := k8sClient.Status().Update(t.Context(), deployment); err != nil {
		t.Fatal(err)
	}
}

func assertCondition(t *testing.T, cr *xhandlerv1alpha1.XHandler, conditionType string, status metav1.ConditionStatus, reason string) {
	t.Helper()
	condition := meta.FindStatusCondition(cr.Status.Conditions, conditionType)
	if condition == nil {
		t.Fatalf("no %s condition in %+v", conditionType, cr.Status.Conditions)
	}
	if condition.Status != status || condition.Reason != reason {
		t.Errorf("%s condition %s/%s (%s), want %s/%s", conditionType, condition.Status, condition.Reason, condition.Message, status, reason)
	}
	if condition.ObservedGeneration != cr.Generation || cr.Status.ObservedGeneration != cr.Generation {
		t.Errorf("observed generation %d (condition %d), want %d", cr.Status.ObservedGeneration, condition.ObservedGeneration, cr.Generation)
	}
}

func assertNotFound(t *testing.T, namespace, name string, obj client.Object) {
	t.Helper()
	err := k8sClient.Get(t.Context(), client.ObjectKey{Namespace: namespace, Name: name}, obj)
	if !apierrors.IsNotFound(err) {
		t.Errorf("get %T %s: %v, want not found", obj, name, err)
	}
}

func assertOwned(t *testing.T, cr *xhandlerv1alpha1.XHandler, objs ...client.Object) {
	t.Helper()
	for _, obj := range objs {
		owner := metav1.GetControllerOf(obj)
		if owner == nil || owner.UID != cr.UID {
			t.Errorf("%s: controller %v, want the XHandler", obj.GetName(), owner)
		}
	}
}

func envOf(container corev1.Container) map[string]corev1.EnvVar {
	env := map[string]corev1.EnvVar{}
	for _, variable := range container.Env {
		env[variable.Name] = variable
	}
	return env
}

func assertEnv(t *testing.T, container corev1.Container, want map[string]string) {
	t.Helper()
	env := envOf(container)
	for name, value := range want {
		variable, ok := env[name]
		switch {
		case value == "" && ok:
			t.Errorf("%s: %s=%q, want it unset", container.Name, name, variable.Value)
		case value != "" && !ok:
			t.Errorf("%s: %s unset, want %q", container.Name, name, value)
		case value != "" && variable.Value != value:
			t.Errorf("%s: %s=%q, want %q", container.Name, name, variable.Value, value)
		}
	}
}

// migrate finishes the migration of image, and reconciles.
func migrate(t *testing.T, cr *xhandlerv1alpha1.XHandler, image string) {
	t.Helper()
	finishJob(t, cr, image, true)
	if result := reconcileXHandler(t, nil, cr); result.RequeueAfter != 0 {
		t.Errorf("requeue after %s, want none once migrated", result.RequeueAfter)
	}
	assertCondition(t, cr, ConditionMigrated, metav1.ConditionTrue, "Succeeded")
}

func TestXHandlerMigratesThenDeploys(t *testing.T) {
	ctx := t.Context()
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{})

	result := reconcileXHandler(t, nil, cr)
	if result.RequeueAfter == 0 {
		t.Error("no requeue while the migration runs")
	}
	assertCondition(t, cr, ConditionMigrated, metav1.ConditionFalse, "Progressing")
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "Migrating")

	// Everything but the api and worker deployments exists while the migration runs
	job := &batchv1.Job{}
	get(ctx, t, cr.Namespace, xhMigrateJobName(cr, testXHandlerImage), job)
	service := &corev1.Service{}
	get(ctx, t, cr.Namespace, "xh-api", service)
	ui := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "xh-ui", ui)
	uiService := &corev1.Service{}
	get(ctx, t, cr.Namespace, "xh-ui", uiService)
	ingress := &networkingv1.Ingress{}
	get(ctx, t, cr.Namespace, "xh", ingress)
	var pdbs []client.Object
	for _, name := range []string{"xh-api", "xh-worker", "xh-ui"} {
		pdb := &policyv1.PodDisruptionBudget{}
		get(ctx, t, cr.Namespace, name, pdb)
		if pdb.Spec.MaxUnavailable == nil || pdb.Spec.MaxUnavailable.IntValue() != 1 {
			t.Errorf("%s: max unavailable %v, want 1", name, pdb.Spec.MaxUnavailable)
		}
		pdbs = append(pdbs, pdb)
	}
	assertOwned(t, cr, append(pdbs, job, service, ui, uiService, ingress)...)
	assertNotFound(t, cr.Namespace, "xh-api", &appsv1.Deployment{})
	assertNotFound(t, cr.Namespace, "xh-worker", &appsv1.Deployment{})
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &appsv1.Deployment{})
	assertNotFound(t, cr.Namespace, "xh-default-deny", &networkingv1.NetworkPolicy{})

	migrateContainer := job.Spec.Template.Spec.Containers[0]
	if migrateContainer.Image != testXHandlerImage {
		t.Errorf("migration image %s, want %s", migrateContainer.Image, testXHandlerImage)
	}
	assertEnv(t, migrateContainer, map[string]string{
		"QUARKUS_INIT_AND_EXIT":           "true",
		"QUARKUS_FLYWAY_MIGRATE_AT_START": "true",
		"XHANDLER_ROLE":                   "migrate",
	})
	if service.Spec.Selector["app.kubernetes.io/component"] != componentAPI {
		t.Errorf("service selector %v, want the api pods", service.Spec.Selector)
	}

	// The job is applied once: reconciling again while it runs keeps it
	reconcileXHandler(t, nil, cr)
	again := &batchv1.Job{}
	get(ctx, t, cr.Namespace, job.Name, again)
	if again.UID != job.UID {
		t.Error("the migration job was replaced")
	}

	migrate(t, cr, testXHandlerImage)
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "Progressing")

	for _, component := range []string{componentAPI, componentWorker} {
		deployment := &appsv1.Deployment{}
		get(ctx, t, cr.Namespace, "xh-"+component, deployment)
		assertOwned(t, cr, deployment)
		if replicas := ptr.Deref(deployment.Spec.Replicas, 0); replicas != 2 {
			t.Errorf("%s: replicas %d, want the default, 2", component, replicas)
		}
		pod := deployment.Spec.Template.Spec
		if ptr.Deref(pod.TerminationGracePeriodSeconds, 0) != 150 {
			t.Errorf("%s: termination grace period %v, want 150", component, pod.TerminationGracePeriodSeconds)
		}
		if ptr.Deref(pod.AutomountServiceAccountToken, true) {
			t.Errorf("%s: the service account token is mounted", component)
		}
		if len(pod.TopologySpreadConstraints) == 0 || ptr.Deref(pod.TopologySpreadConstraints[0].NodeTaintsPolicy, "") != corev1.NodeInclusionPolicyHonor {
			t.Errorf("%s: topology spread %+v, want the zones, honoring the taints", component, pod.TopologySpreadConstraints)
		}
		engine := pod.InitContainers[0]
		if engine.Image != testEngineImage || ptr.Deref(engine.RestartPolicy, "") != corev1.ContainerRestartPolicyAlways {
			t.Errorf("%s: engine %s, restart policy %v, want a native sidecar of %s", component, engine.Image, engine.RestartPolicy, testEngineImage)
		}
		if engine.LivenessProbe == nil || engine.LivenessProbe.Exec == nil || engine.LivenessProbe.Exec.Command[1] != "--health" {
			t.Errorf("%s: engine liveness %+v, want its --health", component, engine.LivenessProbe)
		}
		assertEnv(t, engine, map[string]string{"XHANDLER_ENGINE_ADDR": "127.0.0.1:8090", "HTTPS_PROXY": ""})
		app := pod.Containers[0]
		if app.Image != testXHandlerImage || app.ImagePullPolicy != corev1.PullAlways {
			t.Errorf("%s: app %s (%s), want %s", component, app.Image, app.ImagePullPolicy, testXHandlerImage)
		}
		if app.ReadinessProbe.HTTPGet.Path != "/q/health/ready" || app.LivenessProbe.HTTPGet.Path != "/q/health/live" {
			t.Errorf("%s: probes %s, %s", component, app.ReadinessProbe.HTTPGet.Path, app.LivenessProbe.HTTPGet.Path)
		}
		if !ptr.Deref(app.SecurityContext.ReadOnlyRootFilesystem, false) || !ptr.Deref(pod.SecurityContext.RunAsNonRoot, false) {
			t.Errorf("%s: security context %+v, %+v", component, app.SecurityContext, pod.SecurityContext)
		}
		assertEnv(t, app, map[string]string{
			"XHANDLER_ROLE":                   component,
			"XHANDLER_ENGINE_URL":             "http://localhost:8090",
			"QUARKUS_FLYWAY_MIGRATE_AT_START": "false",
			"XHANDLER_EGRESS_PROXY":           "",
		})
	}

	for _, name := range []string{"xh-api", "xh-worker"} {
		makeAvailable(t, cr.Namespace, name)
	}
	reconcileXHandler(t, nil, cr)
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "Progressing")
	if message := meta.FindStatusCondition(cr.Status.Conditions, ConditionReady).Message; !strings.Contains(message, "xh-ui") {
		t.Errorf("ready message %q, want the UI it waits for", message)
	}

	makeAvailable(t, cr.Namespace, "xh-ui")
	reconcileXHandler(t, nil, cr)
	assertCondition(t, cr, ConditionReady, metav1.ConditionTrue, "Available")
}

func TestXHandlerNewImageRunsANewMigration(t *testing.T) {
	ctx := t.Context()
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{})
	reconcileXHandler(t, nil, cr)
	migrate(t, cr, testXHandlerImage)
	oldJob := xhMigrateJobName(cr, testXHandlerImage)

	const image = "quay.io/example/xhandler-service:v2"
	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) { spec.Image = image })
	reconcileXHandler(t, nil, cr)
	assertCondition(t, cr, ConditionMigrated, metav1.ConditionFalse, "Progressing")

	newJob := &batchv1.Job{}
	get(ctx, t, cr.Namespace, xhMigrateJobName(cr, image), newJob)
	if newJob.Name == oldJob {
		t.Fatal("the same job name for two images")
	}
	if got := newJob.Spec.Template.Spec.Containers[0].Image; got != image {
		t.Errorf("migration image %s, want %s", got, image)
	}
	// The old pods keep running the old image while the new one migrates
	api := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "xh-api", api)
	if got := api.Spec.Template.Spec.Containers[0].Image; got != testXHandlerImage {
		t.Errorf("api image %s while migrating, want the old one", got)
	}

	migrate(t, cr, image)
	get(ctx, t, cr.Namespace, "xh-api", api)
	if got := api.Spec.Template.Spec.Containers[0].Image; got != image {
		t.Errorf("api image %s once migrated, want %s", got, image)
	}
	assertNotFound(t, cr.Namespace, oldJob, &batchv1.Job{})
}

func TestXHandlerFailedMigration(t *testing.T) {
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{})
	reconcileXHandler(t, nil, cr)
	finishJob(t, cr, testXHandlerImage, false)

	if result := reconcileXHandler(t, nil, cr); result.RequeueAfter != 0 {
		t.Errorf("requeue after %s, want none: the job's deletion reconciles", result.RequeueAfter)
	}
	assertCondition(t, cr, ConditionMigrated, metav1.ConditionFalse, "Failed")
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "MigrationFailed")
	assertNotFound(t, cr.Namespace, "xh-api", &appsv1.Deployment{})

	// Deleting the failed job runs it again
	job := &batchv1.Job{ObjectMeta: metav1.ObjectMeta{Name: xhMigrateJobName(cr, testXHandlerImage), Namespace: cr.Namespace}}
	if err := k8sClient.Delete(t.Context(), job, client.PropagationPolicy(metav1.DeletePropagationBackground)); err != nil {
		t.Fatal(err)
	}
	waitFor(t, "the deletion of the job", func() bool {
		return apierrors.IsNotFound(k8sClient.Get(t.Context(), client.ObjectKeyFromObject(job), &batchv1.Job{}))
	})
	reconcileXHandler(t, nil, cr)
	assertCondition(t, cr, ConditionMigrated, metav1.ConditionFalse, "Progressing")
	get(t.Context(), t, cr.Namespace, job.Name, &batchv1.Job{})
}

func TestXHandlerSpec(t *testing.T) {
	ctx := t.Context()
	secret := func(name, key string) *corev1.SecretKeySelector {
		return &corev1.SecretKeySelector{LocalObjectReference: corev1.LocalObjectReference{Name: name}, Key: key}
	}
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{
		API:              &xhandlerv1alpha1.RoleSpec{Instances: ptr.To[int32](3)},
		Worker:           &xhandlerv1alpha1.RoleSpec{Instances: ptr.To[int32](4)},
		ImagePullSecrets: []corev1.LocalObjectReference{{Name: "registry"}},
		Database: &xhandlerv1alpha1.DatabaseSpec{
			URL:                 "jdbc:postgresql://db:5432/xhandler",
			UsernameSecret:      secret("db-app", "username"),
			PasswordSecret:      secret("db-app", "password"),
			OwnerUsernameSecret: secret("db-owner", "username"),
			OwnerPasswordSecret: secret("db-owner", "password"),
		},
		OIDC: &xhandlerv1alpha1.OIDCSpec{
			ServerURL:         "https://keycloak.example.com/realms/xhandler",
			ClientID:          "xhandler-service",
			CredentialsSecret: secret("oidc", "secret"),
		},
		Vault: &xhandlerv1alpha1.VaultSpec{
			PKCS11Library:   "/opt/hsm/libCryptoki2.so",
			PKCS11PinSecret: secret("hsm", "pin"),
			ModuleVolume:    &corev1.VolumeSource{ConfigMap: &corev1.ConfigMapVolumeSource{LocalObjectReference: corev1.LocalObjectReference{Name: "hsm-module"}}},
		},
		Storage: &xhandlerv1alpha1.StorageSpec{
			S3Endpoint:      "https://s3.example.com",
			Bucket:          "documents",
			Region:          "sa-east-1",
			AccessKeySecret: secret("s3", "access-key"),
			SecretKeySecret: secret("s3", "secret-key"),
		},
		SunatFakeURL: "http://sunat-fake:8099",
		OSE:          &xhandlerv1alpha1.OSESpec{RUC: "20123456789", Environment: "beta", ForwardURL: "https://ose.sunat.gob.pe/billService"},
		Hostname:     "xhandler.example.com",
		TLS:          &xhandlerv1alpha1.TLSSpec{Secret: "xhandler-tls"},
		AdditionalOptions: []xhandlerv1alpha1.ValueOrSecret{
			{Name: "XHANDLER_EMIT_PERMITS", Value: "8"},
			{Name: "XHANDLER_LAGO_API_KEY", Secret: secret("lago", "api-key")},
			// Replaces the operator's value
			{Name: "XHANDLER_PUBLIC_URL", Value: "https://facturas.example.com"},
		},
	})
	reconcileXHandler(t, nil, cr)

	job := &batchv1.Job{}
	get(ctx, t, cr.Namespace, xhMigrateJobName(cr, testXHandlerImage), job)
	migrateEnv := envOf(job.Spec.Template.Spec.Containers[0])
	if ref := migrateEnv["QUARKUS_DATASOURCE_USERNAME"].ValueFrom; ref == nil || ref.SecretKeyRef.Name != "db-owner" {
		t.Errorf("migration username from %+v, want the owner's secret", ref)
	}
	if migrateEnv["QUARKUS_OIDC_AUTH_SERVER_URL"].Value != "https://keycloak.example.com/realms/xhandler" {
		t.Error("the migration lacks the OIDC server")
	}
	if _, ok := migrateEnv["XHANDLER_EMIT_PERMITS"]; ok {
		t.Error("the migration has the additional options")
	}
	if job.Spec.Template.Spec.ImagePullSecrets[0].Name != "registry" {
		t.Error("the migration lacks the pull secret")
	}

	migrate(t, cr, testXHandlerImage)
	api := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "xh-api", api)
	worker := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "xh-worker", worker)
	if ptr.Deref(api.Spec.Replicas, 0) != 3 || ptr.Deref(worker.Spec.Replicas, 0) != 4 {
		t.Errorf("replicas %d and %d, want 3 and 4", ptr.Deref(api.Spec.Replicas, 0), ptr.Deref(worker.Spec.Replicas, 0))
	}
	pod := api.Spec.Template.Spec
	app := pod.Containers[0]
	assertEnv(t, app, map[string]string{
		"QUARKUS_DATASOURCE_JDBC_URL":   "jdbc:postgresql://db:5432/xhandler",
		"QUARKUS_OIDC_AUTH_SERVER_URL":  "https://keycloak.example.com/realms/xhandler",
		"QUARKUS_OIDC_CLIENT_ID":        "xhandler-service",
		"XHANDLER_VAULT_PKCS11_LIBRARY": "/opt/hsm/libCryptoki2.so",
		"XHANDLER_STORAGE_S3_ENDPOINT":  "https://s3.example.com",
		"XHANDLER_STORAGE_S3_BUCKET":    "documents",
		"XHANDLER_STORAGE_S3_REGION":    "sa-east-1",
		"XHANDLER_OSE_RUC":              "20123456789",
		"XHANDLER_OSE_ENVIRONMENT":      "beta",
		"XHANDLER_OSE_FORWARD_URL":      "https://ose.sunat.gob.pe/billService",
		"XHANDLER_SUNAT_FAKE_URL":       "http://sunat-fake:8099",
		"XHANDLER_EMIT_PERMITS":         "8",
		"XHANDLER_PUBLIC_URL":           "https://facturas.example.com",
	})
	env := envOf(app)
	for name, secretName := range map[string]string{
		"QUARKUS_DATASOURCE_USERNAME":     "db-app",
		"QUARKUS_DATASOURCE_PASSWORD":     "db-app",
		"QUARKUS_OIDC_CREDENTIALS_SECRET": "oidc",
		"XHANDLER_VAULT_PKCS11_PIN":       "hsm",
		"XHANDLER_STORAGE_S3_ACCESS_KEY":  "s3",
		"XHANDLER_STORAGE_S3_SECRET_KEY":  "s3",
		"XHANDLER_LAGO_API_KEY":           "lago",
	} {
		if ref := env[name].ValueFrom; ref == nil || ref.SecretKeyRef == nil || ref.SecretKeyRef.Name != secretName {
			t.Errorf("%s from %+v, want secret %s", name, ref, secretName)
		}
	}
	if !slices.ContainsFunc(app.VolumeMounts, func(m corev1.VolumeMount) bool {
		return m.Name == "hsm-module" && m.MountPath == "/opt/hsm" && m.ReadOnly
	}) {
		t.Errorf("mounts %+v, want the HSM module at /opt/hsm", app.VolumeMounts)
	}
	if !slices.ContainsFunc(pod.Volumes, func(v corev1.Volume) bool {
		return v.Name == "hsm-module" && v.ConfigMap != nil && v.ConfigMap.Name == "hsm-module"
	}) {
		t.Errorf("volumes %+v, want the HSM module", pod.Volumes)
	}
	assertEnv(t, pod.InitContainers[0], map[string]string{"XHANDLER_SUNAT_FAKE_URL": "http://sunat-fake:8099"})

	ingress := &networkingv1.Ingress{}
	get(ctx, t, cr.Namespace, "xh", ingress)
	if len(ingress.Spec.TLS) != 1 || ingress.Spec.TLS[0].SecretName != "xhandler-tls" || ingress.Spec.TLS[0].Hosts[0] != "xhandler.example.com" {
		t.Errorf("ingress TLS %+v", ingress.Spec.TLS)
	}
	rule := ingress.Spec.Rules[0]
	if rule.Host != "xhandler.example.com" {
		t.Errorf("ingress host %q", rule.Host)
	}
	backends := map[string]string{}
	for _, path := range rule.HTTP.Paths {
		backends[path.Path] = path.Backend.Service.Name
	}
	if backends["/"] != "xh-ui" || backends["/api"] != "xh-api" || backends["/ol-ti-itcpfegem"] != "xh-api" {
		t.Errorf("ingress paths %v, want the UI, and the APIs to the api", backends)
	}
}

func TestXHandlerPublicURLFromTheHostname(t *testing.T) {
	cr := &xhandlerv1alpha1.XHandler{Spec: xhandlerv1alpha1.XHandlerSpec{Hostname: "xhandler.example.com"}}
	if got := xhPublicURL(cr); got != "http://xhandler.example.com" {
		t.Errorf("public URL %q", got)
	}
	cr.Spec.TLS = &xhandlerv1alpha1.TLSSpec{Secret: "tls"}
	if got := xhPublicURL(cr); got != "https://xhandler.example.com" {
		t.Errorf("public URL with TLS %q", got)
	}
	if got := xhPublicURL(&xhandlerv1alpha1.XHandler{}); got != "" {
		t.Errorf("public URL without hostname %q, want none", got)
	}
}

func TestXHandlerIngressAndUIToggles(t *testing.T) {
	ctx := t.Context()
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{})
	reconcileXHandler(t, nil, cr)

	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) {
		spec.UI = &xhandlerv1alpha1.UISpec{Enabled: ptr.To(false)}
	})
	reconcileXHandler(t, nil, cr)
	assertNotFound(t, cr.Namespace, "xh-ui", &appsv1.Deployment{})
	assertNotFound(t, cr.Namespace, "xh-ui", &corev1.Service{})
	assertNotFound(t, cr.Namespace, "xh-ui", &policyv1.PodDisruptionBudget{})
	ingress := &networkingv1.Ingress{}
	get(ctx, t, cr.Namespace, "xh", ingress)
	paths := ingress.Spec.Rules[0].HTTP.Paths
	if len(paths) != 1 || paths[0].Backend.Service.Name != "xh-api" {
		t.Errorf("ingress paths %+v, want everything to the api without the UI", paths)
	}

	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) {
		spec.Ingress = &xhandlerv1alpha1.IngressSpec{Enabled: ptr.To(false)}
	})
	reconcileXHandler(t, nil, cr)
	reconcileXHandler(t, nil, cr)
	assertNotFound(t, cr.Namespace, "xh", &networkingv1.Ingress{})
}

func TestXHandlerEgressProxyAndNetworkPolicies(t *testing.T) {
	ctx := t.Context()
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{SunatFakeURL: "http://sunat-fake:8099"})
	reconcileXHandler(t, nil, cr)
	migrate(t, cr, testXHandlerImage)

	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) {
		spec.EgressProxy = &xhandlerv1alpha1.EgressProxySpec{Enabled: ptr.To(true)}
		spec.NetworkPolicies = &xhandlerv1alpha1.NetworkPoliciesSpec{
			Enabled:             ptr.To(true),
			DatabasePodSelector: &metav1.LabelSelector{MatchLabels: map[string]string{"cnpg.io/cluster": "xhandler-db"}},
		}
	})
	reconcileXHandler(t, nil, cr)

	proxy := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "xh-egress-proxy", proxy)
	if image := proxy.Spec.Template.Spec.Containers[0].Image; image != testEgressProxyImage {
		t.Errorf("proxy image %s, want %s", image, testEgressProxyImage)
	}
	configMap := &corev1.ConfigMap{}
	get(ctx, t, cr.Namespace, "xh-egress-proxy", configMap)
	if !strings.Contains(configMap.Data["squid.conf"], "http_access deny private") {
		t.Errorf("squid.conf %q, want the private ranges denied", configMap.Data["squid.conf"])
	}
	get(ctx, t, cr.Namespace, "xh-egress-proxy", &corev1.Service{})
	get(ctx, t, cr.Namespace, "xh-egress-proxy", &policyv1.PodDisruptionBudget{})

	api := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "xh-api", api)
	assertEnv(t, api.Spec.Template.Spec.InitContainers[0], map[string]string{
		"HTTPS_PROXY": "http://xh-egress-proxy:3128",
		"NO_PROXY":    "localhost,127.0.0.1,.svc,.cluster.local,sunat-fake",
	})
	assertEnv(t, api.Spec.Template.Spec.Containers[0], map[string]string{"XHANDLER_EGRESS_PROXY": "xh-egress-proxy:3128"})

	for _, name := range []string{"xh-default-deny", "xh-ingress", "xh-egress", "xh-migrate", "xh-egress-proxy"} {
		policy := &networkingv1.NetworkPolicy{}
		get(ctx, t, cr.Namespace, name, policy)
		assertOwned(t, cr, policy)
	}
	egress := &networkingv1.NetworkPolicy{}
	get(ctx, t, cr.Namespace, "xh-egress", egress)
	if !slices.ContainsFunc(egress.Spec.Egress, func(rule networkingv1.NetworkPolicyEgressRule) bool {
		return len(rule.To) == 1 && rule.To[0].PodSelector != nil && rule.To[0].PodSelector.MatchLabels["cnpg.io/cluster"] == "xhandler-db"
	}) {
		t.Errorf("egress rules %+v, want the database pods", egress.Spec.Egress)
	}
	ingress := &networkingv1.NetworkPolicy{}
	get(ctx, t, cr.Namespace, "xh-ingress", ingress)
	if values := ingress.Spec.Ingress[0].From[0].NamespaceSelector.MatchExpressions[0].Values; !slices.Equal(values, defaultIngressNamespaces) {
		t.Errorf("ingress namespaces %v, want %v", values, defaultIngressNamespaces)
	}

	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) {
		spec.EgressProxy.Enabled = ptr.To(false)
	})
	reconcileXHandler(t, nil, cr)
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &appsv1.Deployment{})
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &corev1.ConfigMap{})
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &corev1.Service{})
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &policyv1.PodDisruptionBudget{})
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &networkingv1.NetworkPolicy{})
	get(ctx, t, cr.Namespace, "xh-egress", &networkingv1.NetworkPolicy{})
	get(ctx, t, cr.Namespace, "xh-api", api)
	assertEnv(t, api.Spec.Template.Spec.InitContainers[0], map[string]string{"HTTPS_PROXY": "", "NO_PROXY": ""})

	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) {
		spec.NetworkPolicies.Enabled = ptr.To(false)
	})
	reconcileXHandler(t, nil, cr)
	for _, name := range []string{"xh-default-deny", "xh-ingress", "xh-egress", "xh-migrate"} {
		assertNotFound(t, cr.Namespace, name, &networkingv1.NetworkPolicy{})
	}
}

func TestXHandlerUISearchpe(t *testing.T) {
	ctx := t.Context()
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{
		OIDC: &xhandlerv1alpha1.OIDCSpec{ServerURL: "https://keycloak.example.com/realms/xhandler", ClientID: "xhandler-ui"},
		UI:   &xhandlerv1alpha1.UISpec{SearchpeRef: "searchpe", Instances: ptr.To[int32](1)},
	})
	reconcileXHandler(t, nil, cr)
	migrate(t, cr, testXHandlerImage)
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "SearchpeNotFound")
	assertNotFound(t, cr.Namespace, "xh-ui", &appsv1.Deployment{})

	searchpe := &v1alpha1.Searchpe{
		ObjectMeta: metav1.ObjectMeta{Name: "searchpe", Namespace: cr.Namespace},
		Spec:       v1alpha1.SearchpeSpec{HTTP: &v1alpha1.HTTPSpec{TLSSecret: "tls"}},
	}
	if err := k8sClient.Create(ctx, searchpe); err != nil {
		t.Fatal(err)
	}
	reconcileXHandler(t, nil, cr)
	ui := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "xh-ui", ui)
	if replicas := ptr.Deref(ui.Spec.Replicas, 0); replicas != 1 {
		t.Errorf("UI replicas %d, want 1", replicas)
	}
	container := ui.Spec.Template.Spec.Containers[0]
	if container.Image != testUIImage {
		t.Errorf("UI image %s, want %s", container.Image, testUIImage)
	}
	assertEnv(t, container, map[string]string{
		"XHANDLER_API_URL": "http://xh-api:8080",
		"SEARCHPE_API_URL": "https://searchpe-searchpe-service:8443",
		"OIDC_SERVER_URL":  "https://keycloak.example.com/realms/xhandler",
		"OIDC_CLIENT_ID":   "xhandler-ui",
	})
}

func TestXHandlerMissingImages(t *testing.T) {
	r := newXHandlerReconciler()
	r.Config = XHandlerConfig{ImagePullPolicy: corev1.PullAlways}
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{})

	reconcileXHandler(t, r, cr)
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "ImageMissing")
	message := meta.FindStatusCondition(cr.Status.Conditions, ConditionReady).Message
	for _, variable := range []string{"RELATED_IMAGE_XHANDLER,", "RELATED_IMAGE_XHANDLER_ENGINE", "RELATED_IMAGE_XHANDLER_UI"} {
		if !strings.Contains(message, variable) {
			t.Errorf("message %q, want %s", message, variable)
		}
	}
	if strings.Contains(message, "RELATED_IMAGE_EGRESS_PROXY") {
		t.Errorf("message %q names the image of the disabled egress proxy", message)
	}
	assertNotFound(t, cr.Namespace, "xh-api", &corev1.Service{})

	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) {
		spec.Image = testXHandlerImage
		spec.EngineImage = testEngineImage
		spec.UIImage = testUIImage
		spec.EgressProxy = &xhandlerv1alpha1.EgressProxySpec{Enabled: ptr.To(true), Image: testEgressProxyImage}
	})
	reconcileXHandler(t, r, cr)
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "Migrating")
}

func TestXHandlerReconcileErrorIsAReadyCondition(t *testing.T) {
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{
		// Rejected by the API server: no "=" in the name of a variable
		AdditionalOptions: []xhandlerv1alpha1.ValueOrSecret{{Name: "A=B", Value: "c"}},
	})
	reconcileXHandler(t, nil, cr)
	finishJob(t, cr, testXHandlerImage, true)
	if _, err := newXHandlerReconciler().Reconcile(t.Context(), ctrl.Request{NamespacedName: client.ObjectKeyFromObject(cr)}); err == nil {
		t.Fatal("reconciled a deployment the API server rejects")
	}
	get(t.Context(), t, cr.Namespace, cr.Name, cr)
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "Error")
}

func TestXHandlerValidation(t *testing.T) {
	namespace := newNamespace(t)
	for name, cr := range map[string]*xhandlerv1alpha1.XHandler{
		"long name": {ObjectMeta: metav1.ObjectMeta{Name: strings.Repeat("x", 41), Namespace: namespace}},
		"environment": {
			ObjectMeta: metav1.ObjectMeta{Name: "environment", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OSE: &xhandlerv1alpha1.OSESpec{Environment: "staging"}},
		},
		"ruc": {
			ObjectMeta: metav1.ObjectMeta{Name: "ruc", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OSE: &xhandlerv1alpha1.OSESpec{RUC: "123"}},
		},
	} {
		if err := k8sClient.Create(t.Context(), cr); !apierrors.IsInvalid(err) {
			t.Errorf("%s: create %v, want invalid", name, err)
		}
	}
}

func TestXHandlerReconcileIgnoresADeletedXHandler(t *testing.T) {
	cr := &xhandlerv1alpha1.XHandler{ObjectMeta: metav1.ObjectMeta{Name: "deleted", Namespace: newNamespace(t)}}
	if _, err := newXHandlerReconciler().Reconcile(t.Context(), ctrl.Request{NamespacedName: client.ObjectKeyFromObject(cr)}); err != nil {
		t.Fatal(err)
	}
}

// The manager reconciles an XHandler when it changes, when one of its resources changes and when
// the Searchpe of its UI changes.
func TestXHandlerManagerWatches(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	mgr, err := ctrl.NewManager(restConfig, ctrl.Options{
		Scheme:                 k8sClient.Scheme(),
		Metrics:                metricsserver.Options{BindAddress: "0"},
		HealthProbeBindAddress: "0",
		Controller:             config.Controller{SkipNameValidation: ptr.To(true)},
	})
	if err != nil {
		t.Fatal(err)
	}
	reconciler := newXHandlerReconciler()
	reconciler.Client = mgr.GetClient()
	if err := reconciler.SetupWithManager(mgr); err != nil {
		t.Fatal(err)
	}
	go func() {
		if err := mgr.Start(ctx); err != nil {
			t.Error(err)
		}
	}()

	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{UI: &xhandlerv1alpha1.UISpec{SearchpeRef: "searchpe"}})
	jobKey := client.ObjectKey{Namespace: cr.Namespace, Name: xhMigrateJobName(cr, testXHandlerImage)}
	waitFor(t, "the migration job", func() bool { return k8sClient.Get(ctx, jobKey, &batchv1.Job{}) == nil })

	// The job's success reconciles
	finishJob(t, cr, testXHandlerImage, true)
	apiKey := client.ObjectKey{Namespace: cr.Namespace, Name: "xh-api"}
	waitFor(t, "the api deployment", func() bool { return k8sClient.Get(ctx, apiKey, &appsv1.Deployment{}) == nil })

	// The Searchpe of the UI reconciles
	searchpe := &v1alpha1.Searchpe{ObjectMeta: metav1.ObjectMeta{Name: "searchpe", Namespace: cr.Namespace}}
	if err := k8sClient.Create(ctx, searchpe); err != nil {
		t.Fatal(err)
	}
	uiKey := client.ObjectKey{Namespace: cr.Namespace, Name: "xh-ui"}
	waitFor(t, "the UI deployment", func() bool { return k8sClient.Get(ctx, uiKey, &appsv1.Deployment{}) == nil })

	// A deleted resource is applied again
	api := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "xh-api", api)
	if err := k8sClient.Delete(ctx, api); err != nil {
		t.Fatal(err)
	}
	waitFor(t, "a new api deployment", func() bool {
		recreated := &appsv1.Deployment{}
		return k8sClient.Get(ctx, apiKey, recreated) == nil && recreated.UID != api.UID
	})

	// The deployments' availability reconciles
	for _, name := range []string{"xh-api", "xh-worker", "xh-ui"} {
		makeAvailable(t, cr.Namespace, name)
	}
	waitFor(t, "the Ready condition", func() bool {
		current := &xhandlerv1alpha1.XHandler{}
		return k8sClient.Get(ctx, client.ObjectKeyFromObject(cr), current) == nil &&
			meta.IsStatusConditionTrue(current.Status.Conditions, ConditionReady)
	})
}
