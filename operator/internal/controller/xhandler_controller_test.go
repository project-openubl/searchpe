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
	"maps"
	"slices"
	"strings"
	"testing"

	appsv1 "k8s.io/api/apps/v1"
	batchv1 "k8s.io/api/batch/v1"
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	policyv1 "k8s.io/api/policy/v1"
	"k8s.io/apimachinery/pkg/api/equality"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/api/meta"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/intstr"
	"k8s.io/utils/ptr"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/cache"
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
	testOIDCServer       = "https://keycloak.example.com/realms/xhandler"
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

// newXHandler creates an XHandler of spec in a new namespace. The UI needs an OIDC server: spec
// gets one when it has none.
func newXHandler(t *testing.T, spec xhandlerv1alpha1.XHandlerSpec) *xhandlerv1alpha1.XHandler {
	t.Helper()
	if spec.OIDC == nil {
		spec.OIDC = &xhandlerv1alpha1.OIDCSpec{ServerURL: testOIDCServer}
	}
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
	get(ctx, t, cr.Namespace, "xh-xhandler", ingress)
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
		if probe := app.StartupProbe; probe == nil || probe.HTTPGet.Path != "/q/health/started" || probe.FailureThreshold != 30 || probe.PeriodSeconds != 5 {
			t.Errorf("%s: startup probe %+v, want /q/health/started, 30 times every 5 s", component, probe)
		}
		if spread := pod.TopologySpreadConstraints[0].WhenUnsatisfiable; spread != corev1.ScheduleAnyway {
			t.Errorf("%s: zone spread %s, want ScheduleAnyway by default", component, spread)
		}
		if deployment.Annotations[xhMigrationAnnotation] != xhMigrationHash(cr, testXHandlerImage) {
			t.Errorf("%s: annotations %v, want the migration", component, deployment.Annotations)
		}
		if deployment.Labels[xhManagedByLabel] != xhManagedBy {
			t.Errorf("%s: labels %v, want %s", component, deployment.Labels, xhManagedBy)
		}
		if !ptr.Deref(app.SecurityContext.ReadOnlyRootFilesystem, false) || !ptr.Deref(pod.SecurityContext.RunAsNonRoot, false) {
			t.Errorf("%s: security context %+v, %+v", component, app.SecurityContext, pod.SecurityContext)
		}
		assertEnv(t, app, map[string]string{
			"XHANDLER_ROLE":                   component,
			"XHANDLER_ENGINE_URL":             "http://localhost:8090",
			"QUARKUS_FLYWAY_MIGRATE_AT_START": "false",
			"XHANDLER_EGRESS_PROXY":           "",
			"QUARKUS_OTEL_SDK_DISABLED":       "true",
		})
		assertEnv(t, engine, map[string]string{"OTEL_EXPORTER_OTLP_ENDPOINT": ""})
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

func TestXHandlerNewDatabaseRunsANewMigration(t *testing.T) {
	secret := func(name, key string) *corev1.SecretKeySelector {
		return &corev1.SecretKeySelector{LocalObjectReference: corev1.LocalObjectReference{Name: name}, Key: key}
	}
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{Database: &xhandlerv1alpha1.DatabaseSpec{
		URL:                 "jdbc:postgresql://db:5432/xhandler",
		OwnerUsernameSecret: secret("db-owner", "username"),
		OwnerPasswordSecret: secret("db-owner", "password"),
	}})
	reconcileXHandler(t, nil, cr)
	migrate(t, cr, testXHandlerImage)
	names := []string{xhMigrateJobName(cr, testXHandlerImage)}

	for _, change := range []func(*xhandlerv1alpha1.DatabaseSpec){
		func(db *xhandlerv1alpha1.DatabaseSpec) { db.URL = "jdbc:postgresql://db2:5432/xhandler" },
		func(db *xhandlerv1alpha1.DatabaseSpec) { db.OwnerUsernameSecret = secret("db-owner2", "username") },
		func(db *xhandlerv1alpha1.DatabaseSpec) { db.OwnerPasswordSecret = secret("db-owner", "password2") },
	} {
		update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) { change(spec.Database) })
		reconcileXHandler(t, nil, cr)
		assertCondition(t, cr, ConditionMigrated, metav1.ConditionFalse, "Progressing")
		name := xhMigrateJobName(cr, testXHandlerImage)
		if slices.Contains(names, name) {
			t.Fatalf("job %s again for a new database or owner", name)
		}
		names = append(names, name)
		migrate(t, cr, testXHandlerImage)
	}
}

// A succeeded Job deleted by someone else (a TTL, a cleanup) isn't run again.
func TestXHandlerPrunedMigration(t *testing.T) {
	ctx := t.Context()
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{})
	reconcileXHandler(t, nil, cr)
	migrate(t, cr, testXHandlerImage)

	job := &batchv1.Job{ObjectMeta: metav1.ObjectMeta{Name: xhMigrateJobName(cr, testXHandlerImage), Namespace: cr.Namespace}}
	if err := k8sClient.Delete(ctx, job, client.PropagationPolicy(metav1.DeletePropagationBackground)); err != nil {
		t.Fatal(err)
	}
	waitFor(t, "the deletion of the job", func() bool {
		return apierrors.IsNotFound(k8sClient.Get(ctx, client.ObjectKeyFromObject(job), &batchv1.Job{}))
	})
	reconcileXHandler(t, nil, cr)
	assertCondition(t, cr, ConditionMigrated, metav1.ConditionTrue, "Succeeded")
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "Progressing")
	assertNotFound(t, cr.Namespace, job.Name, &batchv1.Job{})

	// A new image does run its migration
	const image = "quay.io/example/xhandler-service:v2"
	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) { spec.Image = image })
	reconcileXHandler(t, nil, cr)
	assertCondition(t, cr, ConditionMigrated, metav1.ConditionFalse, "Progressing")
	get(ctx, t, cr.Namespace, xhMigrateJobName(cr, image), &batchv1.Job{})
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
			// Not in the migration Job: it logs in as the owner
			{Name: "QUARKUS_DATASOURCE_USERNAME", Value: "someone"},
		},
		Engine: &xhandlerv1alpha1.EngineSpec{AdditionalOptions: []xhandlerv1alpha1.ValueOrSecret{
			{Name: "RUST_LOG", Value: "debug"},
			{Name: "XHANDLER_SUNAT_FAKE_URL", Value: "http://other-fake:8099"},
		}},
		OTel:       &xhandlerv1alpha1.OTelSpec{Endpoint: "http://otel-collector.monitoring:4318"},
		ZoneSpread: "Required",
		Ingress:    &xhandlerv1alpha1.IngressSpec{ClassName: "nginx"},
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
	if migrateEnv["XHANDLER_EMIT_PERMITS"].Value != "8" {
		t.Error("the migration lacks the additional options")
	}
	if migrateEnv["QUARKUS_OTEL_SDK_DISABLED"].Value != "true" {
		t.Error("the migration exports traces")
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
		"QUARKUS_DATASOURCE_JDBC_URL":         "jdbc:postgresql://db:5432/xhandler",
		"QUARKUS_OIDC_AUTH_SERVER_URL":        "https://keycloak.example.com/realms/xhandler",
		"QUARKUS_OIDC_CLIENT_ID":              "xhandler-service",
		"XHANDLER_VAULT_PKCS11_LIBRARY":       "/opt/hsm/libCryptoki2.so",
		"XHANDLER_STORAGE_S3_ENDPOINT":        "https://s3.example.com",
		"XHANDLER_STORAGE_S3_BUCKET":          "documents",
		"XHANDLER_STORAGE_S3_REGION":          "sa-east-1",
		"XHANDLER_OSE_RUC":                    "20123456789",
		"XHANDLER_OSE_ENVIRONMENT":            "beta",
		"XHANDLER_OSE_FORWARD_URL":            "https://ose.sunat.gob.pe/billService",
		"XHANDLER_SUNAT_FAKE_URL":             "http://sunat-fake:8099",
		"XHANDLER_EMIT_PERMITS":               "8",
		"XHANDLER_PUBLIC_URL":                 "https://facturas.example.com",
		"QUARKUS_DATASOURCE_USERNAME":         "someone",
		"QUARKUS_OTEL_SDK_DISABLED":           "",
		"QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT": "http://otel-collector.monitoring:4318",
		"QUARKUS_OTEL_EXPORTER_OTLP_PROTOCOL": "http/protobuf",
	})
	if spread := pod.TopologySpreadConstraints[0].WhenUnsatisfiable; spread != corev1.DoNotSchedule {
		t.Errorf("zone spread %s, want DoNotSchedule when required", spread)
	}
	env := envOf(app)
	for name, secretName := range map[string]string{
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
	assertEnv(t, pod.InitContainers[0], map[string]string{
		"XHANDLER_SUNAT_FAKE_URL":     "http://other-fake:8099",
		"RUST_LOG":                    "debug",
		"OTEL_EXPORTER_OTLP_ENDPOINT": "http://otel-collector.monitoring:4318",
	})

	ingress := &networkingv1.Ingress{}
	get(ctx, t, cr.Namespace, "xh-xhandler", ingress)
	if ptr.Deref(ingress.Spec.IngressClassName, "") != "nginx" {
		t.Errorf("ingress class %v, want nginx", ingress.Spec.IngressClassName)
	}
	for name, value := range map[string]string{
		"nginx.ingress.kubernetes.io/proxy-body-size":    "16m",
		"nginx.ingress.kubernetes.io/proxy-read-timeout": "150",
		"nginx.ingress.kubernetes.io/proxy-send-timeout": "150",
	} {
		if ingress.Annotations[name] != value {
			t.Errorf("ingress annotation %s=%q, want %q", name, ingress.Annotations[name], value)
		}
	}
	if len(ingress.Spec.TLS) != 1 || ingress.Spec.TLS[0].SecretName != "xhandler-tls" || ingress.Spec.TLS[0].Hosts[0] != "xhandler.example.com" {
		t.Errorf("ingress TLS %+v", ingress.Spec.TLS)
	}
	rule := ingress.Spec.Rules[0]
	if rule.Host != "xhandler.example.com" {
		t.Errorf("ingress host %q", rule.Host)
	}
	want := map[string]string{
		"Prefix /":                "xh-ui",
		"Prefix /api/xhandler":    "xh-ui",
		"Prefix /api/searchpe":    "xh-ui",
		"Prefix /api/v1":          "xh-api",
		"Prefix /api/admin":       "xh-api",
		"Prefix /ose":             "xh-api",
		"Prefix /ol-ti-itcpfegem": "xh-api",
		"Prefix /q/openapi":       "xh-api",
		"Exact /q/openapi.json":   "xh-api",
		"Prefix /q/swagger-ui":    "xh-api",
	}
	if got := ingressBackends(ingress); !maps.Equal(got, want) {
		t.Errorf("ingress paths %v, want %v", got, want)
	}
}

// ingressBackends are the services of the paths of the first rule of ingress, by "type path".
func ingressBackends(ingress *networkingv1.Ingress) map[string]string {
	backends := map[string]string{}
	for _, path := range ingress.Spec.Rules[0].HTTP.Paths {
		backends[string(ptr.Deref(path.PathType, ""))+" "+path.Path] = path.Backend.Service.Name
	}
	return backends
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
	get(ctx, t, cr.Namespace, "xh-xhandler", ingress)
	// Only the APIs: not / nor the rest of /q (metrics, health)
	want := map[string]string{
		"Prefix /api/v1":          "xh-api",
		"Prefix /api/admin":       "xh-api",
		"Prefix /ose":             "xh-api",
		"Prefix /ol-ti-itcpfegem": "xh-api",
		"Prefix /q/openapi":       "xh-api",
		"Exact /q/openapi.json":   "xh-api",
		"Prefix /q/swagger-ui":    "xh-api",
	}
	if got := ingressBackends(ingress); !maps.Equal(got, want) {
		t.Errorf("ingress paths %v without the UI, want %v", got, want)
	}

	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) {
		spec.Ingress = &xhandlerv1alpha1.IngressSpec{Enabled: ptr.To(false)}
	})
	reconcileXHandler(t, nil, cr)
	reconcileXHandler(t, nil, cr)
	assertNotFound(t, cr.Namespace, "xh-xhandler", &networkingv1.Ingress{})
}

func TestXHandlerIngressLeavesOthersAlone(t *testing.T) {
	ctx := t.Context()
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{})
	pathType := networkingv1.PathTypePrefix
	backend := networkingv1.IngressBackend{Service: &networkingv1.IngressServiceBackend{
		Name: "other", Port: networkingv1.ServiceBackendPort{Number: 80}}}
	other := &networkingv1.Ingress{
		ObjectMeta: metav1.ObjectMeta{Name: "xh", Namespace: cr.Namespace},
		Spec: networkingv1.IngressSpec{Rules: []networkingv1.IngressRule{{IngressRuleValue: networkingv1.IngressRuleValue{
			HTTP: &networkingv1.HTTPIngressRuleValue{Paths: []networkingv1.HTTPIngressPath{{Path: "/", PathType: &pathType, Backend: backend}}},
		}}}},
	}
	if err := k8sClient.Create(ctx, other); err != nil {
		t.Fatal(err)
	}
	reconcileXHandler(t, nil, cr)
	get(ctx, t, cr.Namespace, "xh", other)
	if metav1.GetControllerOf(other) != nil || other.Spec.Rules[0].HTTP.Paths[0].Backend.Service.Name != "other" {
		t.Errorf("an unrelated ingress named after the XHandler was changed: %+v", other)
	}
	get(ctx, t, cr.Namespace, "xh-xhandler", &networkingv1.Ingress{})

	// The ingress of the first version of the operator, named after the XHandler and owned by it, is deleted
	if err := k8sClient.Delete(ctx, other); err != nil {
		t.Fatal(err)
	}
	previous := &networkingv1.Ingress{
		ObjectMeta: metav1.ObjectMeta{Name: "xh", Namespace: cr.Namespace, OwnerReferences: []metav1.OwnerReference{{
			APIVersion: xhandlerv1alpha1.GroupVersion.String(), Kind: "XHandler", Name: cr.Name, UID: cr.UID, Controller: ptr.To(true),
		}}},
		Spec: other.Spec,
	}
	waitFor(t, "the deletion of the unrelated ingress", func() bool {
		return apierrors.IsNotFound(k8sClient.Get(ctx, client.ObjectKeyFromObject(other), &networkingv1.Ingress{}))
	})
	if err := k8sClient.Create(ctx, previous); err != nil {
		t.Fatal(err)
	}
	reconcileXHandler(t, nil, cr)
	waitFor(t, "the deletion of the previous ingress", func() bool {
		return apierrors.IsNotFound(k8sClient.Get(ctx, client.ObjectKeyFromObject(previous), &networkingv1.Ingress{}))
	})
}

func TestXHandlerEgressProxyAndNetworkPolicies(t *testing.T) {
	ctx := t.Context()
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{
		SunatFakeURL: "http://sunat-fake:8099",
		OTel:         &xhandlerv1alpha1.OTelSpec{Endpoint: "http://otel-collector:4318"},
	})
	reconcileXHandler(t, nil, cr)
	migrate(t, cr, testXHandlerImage)

	sunatFake := networkingv1.NetworkPolicyEgressRule{
		To:    []networkingv1.NetworkPolicyPeer{{PodSelector: &metav1.LabelSelector{MatchLabels: map[string]string{"app": "sunat-fake"}}}},
		Ports: []networkingv1.NetworkPolicyPort{{Port: ptr.To(intstr.FromInt32(8099))}},
	}
	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) {
		spec.EgressProxy = &xhandlerv1alpha1.EgressProxySpec{Enabled: ptr.To(true)}
		spec.NetworkPolicies = &xhandlerv1alpha1.NetworkPoliciesSpec{
			Enabled:             ptr.To(true),
			IngressNamespaces:   []string{"ingress-nginx"},
			DatabasePodSelector: &metav1.LabelSelector{MatchLabels: map[string]string{"cnpg.io/cluster": "xhandler-db"}},
			AdditionalEgress:    []networkingv1.NetworkPolicyEgressRule{sunatFake},
		}
	})
	reconcileXHandler(t, nil, cr)

	proxy := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "xh-egress-proxy", proxy)
	squid := proxy.Spec.Template.Spec.Containers[0]
	if squid.Image != testEgressProxyImage || squid.ImagePullPolicy != corev1.PullIfNotPresent {
		t.Errorf("proxy image %s (%s), want %s if not present", squid.Image, squid.ImagePullPolicy, testEgressProxyImage)
	}
	if podSecurity := proxy.Spec.Template.Spec.SecurityContext; ptr.Deref(podSecurity.RunAsUser, 0) != 31 || !ptr.Deref(podSecurity.RunAsNonRoot, false) {
		t.Errorf("proxy security context %+v, want non-root, 31 by default", podSecurity)
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
		"NO_PROXY":    "localhost,127.0.0.1,.svc,.cluster.local,sunat-fake,otel-collector",
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
	if !slices.ContainsFunc(egress.Spec.Egress, func(rule networkingv1.NetworkPolicyEgressRule) bool {
		return equality.Semantic.DeepEqual(rule.To, sunatFake.To) && len(rule.Ports) == 1 && rule.Ports[0].Port.IntValue() == 8099
	}) {
		t.Errorf("egress rules %+v, want the additional one", egress.Spec.Egress)
	}
	ingress := &networkingv1.NetworkPolicy{}
	get(ctx, t, cr.Namespace, "xh-ingress", ingress)
	if values := ingress.Spec.Ingress[0].From[0].NamespaceSelector.MatchExpressions[0].Values; !slices.Equal(values, []string{"ingress-nginx"}) {
		t.Errorf("ingress namespaces %v, want ingress-nginx", values)
	}

	// Disabled while a migration runs: the proxy stays as long as the old pods use it
	const image = "quay.io/example/xhandler-service:v2"
	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) {
		spec.EgressProxy.Enabled = ptr.To(false)
		spec.Image = image
	})
	reconcileXHandler(t, nil, cr)
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "Migrating")
	get(ctx, t, cr.Namespace, "xh-egress-proxy", &appsv1.Deployment{})
	get(ctx, t, cr.Namespace, "xh-egress-proxy", &corev1.Service{})
	get(ctx, t, cr.Namespace, "xh-egress-proxy", &networkingv1.NetworkPolicy{})
	get(ctx, t, cr.Namespace, "xh-egress", egress)
	if !slices.ContainsFunc(egress.Spec.Egress, func(rule networkingv1.NetworkPolicyEgressRule) bool {
		return len(rule.Ports) == 1 && rule.Ports[0].Port.IntValue() == egressProxyPort
	}) {
		t.Errorf("egress rules %+v, want the proxy while the pods use it", egress.Spec.Egress)
	}

	// Once the Deployments no longer use it, it goes
	migrate(t, cr, image)
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &appsv1.Deployment{})
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &corev1.ConfigMap{})
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &corev1.Service{})
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &policyv1.PodDisruptionBudget{})
	assertNotFound(t, cr.Namespace, "xh-egress-proxy", &networkingv1.NetworkPolicy{})
	get(ctx, t, cr.Namespace, "xh-egress", egress)
	if slices.ContainsFunc(egress.Spec.Egress, func(rule networkingv1.NetworkPolicyEgressRule) bool {
		return len(rule.Ports) == 1 && rule.Ports[0].Port.IntValue() == egressProxyPort
	}) {
		t.Errorf("egress rules %+v, want no proxy", egress.Spec.Egress)
	}
	get(ctx, t, cr.Namespace, "xh-api", api)
	assertEnv(t, api.Spec.Template.Spec.InitContainers[0], map[string]string{"HTTPS_PROXY": "", "NO_PROXY": ""})
	assertEnv(t, api.Spec.Template.Spec.Containers[0], map[string]string{"XHANDLER_EGRESS_PROXY": ""})

	update(t, cr, func(spec *xhandlerv1alpha1.XHandlerSpec) {
		spec.NetworkPolicies.Enabled = ptr.To(false)
	})
	reconcileXHandler(t, nil, cr)
	for _, name := range []string{"xh-default-deny", "xh-ingress", "xh-egress", "xh-migrate"} {
		assertNotFound(t, cr.Namespace, name, &networkingv1.NetworkPolicy{})
	}
}

func TestXHandlerEgressProxyUser(t *testing.T) {
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{
		EgressProxy: &xhandlerv1alpha1.EgressProxySpec{Enabled: ptr.To(true), RunAsUser: ptr.To[int64](1000680000)},
	})
	reconcileXHandler(t, nil, cr)
	proxy := &appsv1.Deployment{}
	get(t.Context(), t, cr.Namespace, "xh-egress-proxy", proxy)
	if user := ptr.Deref(proxy.Spec.Template.Spec.SecurityContext.RunAsUser, 0); user != 1000680000 {
		t.Errorf("proxy user %d, want the spec's", user)
	}
}

// A Searchpe's deletion takes SEARCHPE_API_URL off the UI, which keeps running.
func TestXHandlerUISearchpe(t *testing.T) {
	ctx := t.Context()
	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{
		OIDC: &xhandlerv1alpha1.OIDCSpec{ServerURL: testOIDCServer, ClientID: "xhandler-service"},
		UI: &xhandlerv1alpha1.UISpec{
			SearchpeRef:           "searchpe",
			Instances:             ptr.To[int32](1),
			LibroReclamacionesURL: "https://example.com/libro",
			CABundleConfigMap:     &corev1.ConfigMapKeySelector{LocalObjectReference: corev1.LocalObjectReference{Name: "searchpe-ca"}, Key: "service-ca.crt"},
		},
	})
	reconcileXHandler(t, nil, cr)
	migrate(t, cr, testXHandlerImage)
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "SearchpeNotFound")
	ui := &appsv1.Deployment{}
	get(ctx, t, cr.Namespace, "xh-ui", ui)
	assertEnv(t, ui.Spec.Template.Spec.Containers[0], map[string]string{"SEARCHPE_API_URL": ""})

	searchpe := &v1alpha1.Searchpe{
		ObjectMeta: metav1.ObjectMeta{Name: "searchpe", Namespace: cr.Namespace},
		Spec:       v1alpha1.SearchpeSpec{HTTP: &v1alpha1.HTTPSpec{TLSSecret: "tls"}},
	}
	if err := k8sClient.Create(ctx, searchpe); err != nil {
		t.Fatal(err)
	}
	reconcileXHandler(t, nil, cr)
	get(ctx, t, cr.Namespace, "xh-ui", ui)
	if replicas := ptr.Deref(ui.Spec.Replicas, 0); replicas != 1 {
		t.Errorf("UI replicas %d, want 1", replicas)
	}
	container := ui.Spec.Template.Spec.Containers[0]
	if container.Image != testUIImage {
		t.Errorf("UI image %s, want %s", container.Image, testUIImage)
	}
	assertEnv(t, container, map[string]string{
		"XHANDLER_API_URL":        "http://xh-api:8080",
		"SEARCHPE_API_URL":        "https://searchpe-searchpe-service:8443",
		"OIDC_SERVER_URL":         testOIDCServer,
		"OIDC_CLIENT_ID":          "xhandler-ui",
		"NODE_EXTRA_CA_CERTS":     "/etc/xhandler-ui/ca/ca.crt",
		"LIBRO_RECLAMACIONES_URL": "https://example.com/libro",
	})
	if container.ReadinessProbe.HTTPGet == nil || container.ReadinessProbe.HTTPGet.Path != "/healthz" ||
		container.LivenessProbe.HTTPGet == nil || container.LivenessProbe.HTTPGet.Path != "/healthz" {
		t.Errorf("UI probes %+v, %+v, want GET /healthz", container.ReadinessProbe, container.LivenessProbe)
	}
	if !slices.ContainsFunc(ui.Spec.Template.Spec.Volumes, func(v corev1.Volume) bool {
		return v.ConfigMap != nil && v.ConfigMap.Name == "searchpe-ca" && len(v.ConfigMap.Items) == 1 &&
			v.ConfigMap.Items[0].Key == "service-ca.crt" && v.ConfigMap.Items[0].Path == "ca.crt"
	}) {
		t.Errorf("UI volumes %+v, want the CA bundle", ui.Spec.Template.Spec.Volumes)
	}

	if err := k8sClient.Delete(ctx, searchpe); err != nil {
		t.Fatal(err)
	}
	waitFor(t, "the deletion of the Searchpe", func() bool {
		return apierrors.IsNotFound(k8sClient.Get(ctx, client.ObjectKeyFromObject(searchpe), &v1alpha1.Searchpe{}))
	})
	reconcileXHandler(t, nil, cr)
	assertCondition(t, cr, ConditionReady, metav1.ConditionFalse, "SearchpeNotFound")
	get(ctx, t, cr.Namespace, "xh-ui", ui)
	assertEnv(t, ui.Spec.Template.Spec.Containers[0], map[string]string{"SEARCHPE_API_URL": "", "OIDC_CLIENT_ID": "xhandler-ui"})
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
		Engine: &xhandlerv1alpha1.EngineSpec{AdditionalOptions: []xhandlerv1alpha1.ValueOrSecret{{Name: "A=B", Value: "c"}}},
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
	oidc := &xhandlerv1alpha1.OIDCSpec{ServerURL: testOIDCServer}
	for name, cr := range map[string]*xhandlerv1alpha1.XHandler{
		"long name":   {ObjectMeta: metav1.ObjectMeta{Name: strings.Repeat("x", 41), Namespace: namespace}, Spec: xhandlerv1alpha1.XHandlerSpec{OIDC: oidc}},
		"digit first": {ObjectMeta: metav1.ObjectMeta{Name: "1xhandler", Namespace: namespace}, Spec: xhandlerv1alpha1.XHandlerSpec{OIDC: oidc}},
		"dot":         {ObjectMeta: metav1.ObjectMeta{Name: "x.handler", Namespace: namespace}, Spec: xhandlerv1alpha1.XHandlerSpec{OIDC: oidc}},
		"environment": {
			ObjectMeta: metav1.ObjectMeta{Name: "environment", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OIDC: oidc, OSE: &xhandlerv1alpha1.OSESpec{Environment: "staging"}},
		},
		"ruc": {
			ObjectMeta: metav1.ObjectMeta{Name: "ruc", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OIDC: oidc, OSE: &xhandlerv1alpha1.OSESpec{RUC: "123"}},
		},
		"ruc on the fake": {
			ObjectMeta: metav1.ObjectMeta{Name: "ruc-fake", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OIDC: oidc, OSE: &xhandlerv1alpha1.OSESpec{RUC: "20123456789", Environment: "fake"}},
		},
		"ruc without environment": {
			ObjectMeta: metav1.ObjectMeta{Name: "ruc-none", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OIDC: oidc, OSE: &xhandlerv1alpha1.OSESpec{RUC: "20123456789"}},
		},
		"option without name": {
			ObjectMeta: metav1.ObjectMeta{Name: "option", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OIDC: oidc, AdditionalOptions: []xhandlerv1alpha1.ValueOrSecret{{Value: "x"}}},
		},
		"UI without OIDC": {ObjectMeta: metav1.ObjectMeta{Name: "no-oidc", Namespace: namespace}},
		"network policies without ingress namespaces": {
			ObjectMeta: metav1.ObjectMeta{Name: "policies", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OIDC: oidc, NetworkPolicies: &xhandlerv1alpha1.NetworkPoliciesSpec{Enabled: ptr.To(true)}},
		},
		"zone spread": {
			ObjectMeta: metav1.ObjectMeta{Name: "zones", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OIDC: oidc, ZoneSpread: "Always"},
		},
	} {
		if err := k8sClient.Create(t.Context(), cr); !apierrors.IsInvalid(err) {
			t.Errorf("%s: create %v, want invalid", name, err)
		}
	}

	for name, cr := range map[string]*xhandlerv1alpha1.XHandler{
		"no UI without OIDC": {
			ObjectMeta: metav1.ObjectMeta{Name: "no-ui", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{UI: &xhandlerv1alpha1.UISpec{Enabled: ptr.To(false)}},
		},
		"ruc on beta": {
			ObjectMeta: metav1.ObjectMeta{Name: "ruc-beta", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OIDC: oidc, OSE: &xhandlerv1alpha1.OSESpec{RUC: "20123456789", Environment: "beta"}},
		},
		"disabled network policies": {
			ObjectMeta: metav1.ObjectMeta{Name: "no-policies", Namespace: namespace},
			Spec:       xhandlerv1alpha1.XHandlerSpec{OIDC: oidc, NetworkPolicies: &xhandlerv1alpha1.NetworkPoliciesSpec{}},
		},
	} {
		if err := k8sClient.Create(t.Context(), cr); err != nil {
			t.Errorf("%s: create %v, want valid", name, err)
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
		// The cache of the operator
		Cache: cache.Options{ByObject: XHandlerCacheByObject()},
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

	cr := newXHandler(t, xhandlerv1alpha1.XHandlerSpec{
		UI:          &xhandlerv1alpha1.UISpec{SearchpeRef: "searchpe"},
		EgressProxy: &xhandlerv1alpha1.EgressProxySpec{Enabled: ptr.To(true)},
	})
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

	// A deleted ConfigMap, of the cache restricted to the operator's, is applied again
	configMap := &corev1.ConfigMap{}
	get(ctx, t, cr.Namespace, "xh-egress-proxy", configMap)
	if err := k8sClient.Delete(ctx, configMap); err != nil {
		t.Fatal(err)
	}
	waitFor(t, "a new egress proxy ConfigMap", func() bool {
		recreated := &corev1.ConfigMap{}
		return k8sClient.Get(ctx, client.ObjectKeyFromObject(configMap), recreated) == nil && recreated.UID != configMap.UID
	})

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
	for _, name := range []string{"xh-api", "xh-worker", "xh-ui", "xh-egress-proxy"} {
		makeAvailable(t, cr.Namespace, name)
	}
	waitFor(t, "the Ready condition", func() bool {
		current := &xhandlerv1alpha1.XHandler{}
		return k8sClient.Get(ctx, client.ObjectKeyFromObject(cr), current) == nil &&
			meta.IsStatusConditionTrue(current.Status.Conditions, ConditionReady)
	})
}
