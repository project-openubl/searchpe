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
	"errors"
	"fmt"
	"slices"
	"strings"
	"time"

	appsv1 "k8s.io/api/apps/v1"
	batchv1 "k8s.io/api/batch/v1"
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	policyv1 "k8s.io/api/policy/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	"k8s.io/apimachinery/pkg/api/meta"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/handler"

	"github.com/project-openubl/searchpe/operator/api/v1alpha1"
	xhandlerv1alpha1 "github.com/project-openubl/searchpe/operator/api/xhandler/v1alpha1"
)

// xhFieldOwner owns the fields the operator applies to the resources of an XHandler.
const xhFieldOwner = client.FieldOwner("xhandler")

// The conditions of an XHandler.
const (
	// ConditionMigrated is true when the migration Job of the current image has succeeded.
	ConditionMigrated = "Migrated"
	// ConditionReady is true when the deployments are available.
	ConditionReady = "Ready"
)

// How often a running migration is checked, on top of the changes of its Job.
const migrationPollInterval = 10 * time.Second

// XHandlerConfig is the configuration of the operator for the XHandler resources.
type XHandlerConfig struct {
	// Image is the xhandler-service image of the resources that don't set one.
	Image string
	// EngineImage is the engine image of the resources that don't set one.
	EngineImage string
	// UIImage is the UI image of the resources that don't set one.
	UIImage string
	// EgressProxyImage is the egress proxy (Squid) image of the resources that don't set one.
	EgressProxyImage string
	// ImagePullPolicy is the pull policy of the images.
	ImagePullPolicy corev1.PullPolicy
}

// XHandlerReconciler runs each XHandler: the migration Job of its image, then the api and worker
// Deployments (the app with the engine as a sidecar), the UI, an Ingress and, optionally, the
// egress proxy and the network policies.
type XHandlerReconciler struct {
	client.Client
	Config XHandlerConfig
}

// +kubebuilder:rbac:groups=xhandler.openubl.io,namespace=system,resources=xhandlers,verbs=get;list;watch
// +kubebuilder:rbac:groups=xhandler.openubl.io,namespace=system,resources=xhandlers/status,verbs=get;update;patch
// +kubebuilder:rbac:groups=xhandler.openubl.io,namespace=system,resources=xhandlers/finalizers,verbs=update
// +kubebuilder:rbac:groups=searchpe.openubl.io,namespace=system,resources=searchpes,verbs=get;list;watch
// +kubebuilder:rbac:groups=apps,namespace=system,resources=deployments,verbs=get;list;watch;create;patch;delete
// +kubebuilder:rbac:groups="",namespace=system,resources=services;configmaps,verbs=get;list;watch;create;patch;delete
// +kubebuilder:rbac:groups=batch,namespace=system,resources=jobs,verbs=get;list;watch;create;patch;delete
// +kubebuilder:rbac:groups=policy,namespace=system,resources=poddisruptionbudgets,verbs=get;list;watch;create;patch;delete
// +kubebuilder:rbac:groups=networking.k8s.io,namespace=system,resources=networkpolicies;ingresses,verbs=get;list;watch;create;patch;delete

// Reconcile brings the resources of an XHandler to the state its spec describes, and reports it
// in its status.
func (r *XHandlerReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	cr := &xhandlerv1alpha1.XHandler{}
	if err := r.Get(ctx, req.NamespacedName, cr); err != nil {
		return ctrl.Result{}, client.IgnoreNotFound(err)
	}
	if !cr.DeletionTimestamp.IsZero() {
		// Kubernetes deletes the resources it owns
		return ctrl.Result{}, nil
	}

	before := cr.DeepCopy()
	result, err := r.reconcile(ctx, cr)
	if err != nil {
		r.setCondition(cr, ConditionReady, metav1.ConditionFalse, "Error", err.Error())
	}
	cr.Status.ObservedGeneration = cr.Generation
	if statusErr := r.Status().Patch(ctx, cr, client.MergeFrom(before)); statusErr != nil {
		err = errors.Join(err, fmt.Errorf("status: %w", statusErr))
	}
	return result, err
}

func (r *XHandlerReconciler) reconcile(ctx context.Context, cr *xhandlerv1alpha1.XHandler) (ctrl.Result, error) {
	images, missing := r.images(cr)
	if len(missing) > 0 {
		r.setCondition(cr, ConditionReady, metav1.ConditionFalse, "ImageMissing",
			"Set "+strings.Join(missing, ", ")+" in the operator's environment, or the image in the spec")
		return ctrl.Result{}, nil
	}

	if err := r.reconcileEgressProxy(ctx, cr, images.egressProxy); err != nil {
		return ctrl.Result{}, fmt.Errorf("egress proxy: %w", err)
	}
	if err := r.reconcileNetworkPolicies(ctx, cr); err != nil {
		return ctrl.Result{}, fmt.Errorf("network policies: %w", err)
	}
	if err := r.apply(ctx, xhService(cr, xhAPIName(cr), xhAppLabel, componentAPI, "http", xhAppPort)); err != nil {
		return ctrl.Result{}, fmt.Errorf("api service: %w", err)
	}
	for _, component := range []string{componentAPI, componentWorker} {
		if err := r.apply(ctx, podDisruptionBudget(cr, xhName(cr, component), xhAppLabel, component)); err != nil {
			return ctrl.Result{}, fmt.Errorf("%s pod disruption budget: %w", component, err)
		}
	}
	searchpeFound, err := r.reconcileUI(ctx, cr, images.ui)
	if err != nil {
		return ctrl.Result{}, fmt.Errorf("ui: %w", err)
	}
	if err := r.reconcileIngress(ctx, cr); err != nil {
		return ctrl.Result{}, fmt.Errorf("ingress: %w", err)
	}

	// The new pods start only once the schema is theirs (expand/contract keeps the old ones working)
	if migrated, result, err := r.reconcileMigration(ctx, cr, images.app); err != nil || !migrated {
		return result, err
	}

	for _, component := range []string{componentAPI, componentWorker} {
		deployment, err := roleDeployment(cr, images, r.Config.ImagePullPolicy, component)
		if err != nil {
			return ctrl.Result{}, fmt.Errorf("%s deployment: %w", component, err)
		}
		if err := r.apply(ctx, deployment); err != nil {
			return ctrl.Result{}, fmt.Errorf("%s deployment: %w", component, err)
		}
	}

	if !searchpeFound {
		r.setCondition(cr, ConditionReady, metav1.ConditionFalse, "SearchpeNotFound",
			fmt.Sprintf("Searchpe %q of the UI not found", cr.Spec.UI.SearchpeRef))
		return ctrl.Result{}, nil
	}
	var unavailable []string
	for _, name := range []string{xhAPIName(cr), xhWorkerName(cr), xhUIName(cr), xhEgressProxyName(cr)} {
		deployment := &appsv1.Deployment{}
		err := r.Get(ctx, client.ObjectKey{Namespace: cr.Namespace, Name: name}, deployment)
		switch {
		case apierrors.IsNotFound(err):
			// Disabled
		case err != nil:
			return ctrl.Result{}, fmt.Errorf("deployment %s: %w", name, err)
		case !deploymentAvailable(deployment):
			unavailable = append(unavailable, name)
		}
	}
	if len(unavailable) > 0 {
		r.setCondition(cr, ConditionReady, metav1.ConditionFalse, "Progressing",
			"Waiting for the deployments "+strings.Join(unavailable, ", "))
		return ctrl.Result{}, nil
	}
	r.setCondition(cr, ConditionReady, metav1.ConditionTrue, "Available", "The deployments are available")
	return ctrl.Result{}, nil
}

// images are the images of cr, and the variables of the operator's default images it needs and
// lacks.
func (r *XHandlerReconciler) images(cr *xhandlerv1alpha1.XHandler) (xhImages, []string) {
	var missing []string
	pick := func(custom, byDefault, variable string, needed bool) string {
		if custom != "" {
			return custom
		}
		if byDefault == "" && needed {
			missing = append(missing, variable)
		}
		return byDefault
	}
	var egressProxyImage string
	if cr.Spec.EgressProxy != nil {
		egressProxyImage = cr.Spec.EgressProxy.Image
	}
	images := xhImages{
		app:         pick(cr.Spec.Image, r.Config.Image, "RELATED_IMAGE_XHANDLER", true),
		engine:      pick(cr.Spec.EngineImage, r.Config.EngineImage, "RELATED_IMAGE_XHANDLER_ENGINE", true),
		ui:          pick(cr.Spec.UIImage, r.Config.UIImage, "RELATED_IMAGE_XHANDLER_UI", xhUIEnabled(cr)),
		egressProxy: pick(egressProxyImage, r.Config.EgressProxyImage, "RELATED_IMAGE_EGRESS_PROXY", xhEgressProxyEnabled(cr)),
	}
	return images, missing
}

func (r *XHandlerReconciler) apply(ctx context.Context, obj runtime.ApplyConfiguration) error {
	return r.Apply(ctx, obj, xhFieldOwner, client.ForceOwnership)
}

// deleteAll deletes the objects that exist.
func (r *XHandlerReconciler) deleteAll(ctx context.Context, objs ...client.Object) error {
	for _, obj := range objs {
		if err := r.Delete(ctx, obj); client.IgnoreNotFound(err) != nil {
			return fmt.Errorf("delete %s: %w", obj.GetName(), err)
		}
	}
	return nil
}

func (r *XHandlerReconciler) setCondition(cr *xhandlerv1alpha1.XHandler, conditionType string, status metav1.ConditionStatus, reason, message string) {
	meta.SetStatusCondition(&cr.Status.Conditions, metav1.Condition{
		Type:               conditionType,
		Status:             status,
		Reason:             reason,
		Message:            message,
		ObservedGeneration: cr.Generation,
	})
}

func objectMeta(cr *xhandlerv1alpha1.XHandler, name string) metav1.ObjectMeta {
	return metav1.ObjectMeta{Name: name, Namespace: cr.Namespace}
}

func (r *XHandlerReconciler) reconcileEgressProxy(ctx context.Context, cr *xhandlerv1alpha1.XHandler, image string) error {
	name := xhEgressProxyName(cr)
	if !xhEgressProxyEnabled(cr) {
		return r.deleteAll(ctx,
			&appsv1.Deployment{ObjectMeta: objectMeta(cr, name)},
			&corev1.Service{ObjectMeta: objectMeta(cr, name)},
			&policyv1.PodDisruptionBudget{ObjectMeta: objectMeta(cr, name)},
			&corev1.ConfigMap{ObjectMeta: objectMeta(cr, name)})
	}
	for _, obj := range []runtime.ApplyConfiguration{
		egressProxyConfigMap(cr),
		egressProxyDeployment(cr, image, r.Config.ImagePullPolicy),
		xhService(cr, name, xhEgressProxyLabel, componentEgressProxy, "proxy", egressProxyPort),
		podDisruptionBudget(cr, name, xhEgressProxyLabel, componentEgressProxy),
	} {
		if err := r.apply(ctx, obj); err != nil {
			return err
		}
	}
	return nil
}

func (r *XHandlerReconciler) reconcileNetworkPolicies(ctx context.Context, cr *xhandlerv1alpha1.XHandler) error {
	var policies []string
	if xhNetworkPoliciesEnabled(cr) {
		for _, policy := range networkPolicies(cr) {
			if err := r.apply(ctx, policy); err != nil {
				return err
			}
			policies = append(policies, *policy.Name)
		}
	}
	for _, name := range xhNetworkPolicyNames(cr) {
		if !slices.Contains(policies, name) {
			if err := r.deleteAll(ctx, &networkingv1.NetworkPolicy{ObjectMeta: objectMeta(cr, name)}); err != nil {
				return err
			}
		}
	}
	return nil
}

// reconcileUI applies the UI, or deletes it when it is disabled. It is false when the Searchpe
// the UI refers to doesn't exist: the UI isn't applied until it does.
func (r *XHandlerReconciler) reconcileUI(ctx context.Context, cr *xhandlerv1alpha1.XHandler, image string) (bool, error) {
	name := xhUIName(cr)
	if !xhUIEnabled(cr) {
		return true, r.deleteAll(ctx,
			&appsv1.Deployment{ObjectMeta: objectMeta(cr, name)},
			&corev1.Service{ObjectMeta: objectMeta(cr, name)},
			&policyv1.PodDisruptionBudget{ObjectMeta: objectMeta(cr, name)})
	}

	var searchpeURL string
	if cr.Spec.UI != nil && cr.Spec.UI.SearchpeRef != "" {
		searchpe := &v1alpha1.Searchpe{}
		err := r.Get(ctx, client.ObjectKey{Namespace: cr.Namespace, Name: cr.Spec.UI.SearchpeRef}, searchpe)
		if apierrors.IsNotFound(err) {
			return false, nil
		}
		if err != nil {
			return false, err
		}
		scheme := "http"
		if tlsEnabled(searchpe) {
			scheme = "https"
		}
		searchpeURL = fmt.Sprintf("%s://%s:%d", scheme, serviceName(searchpe), servicePort(searchpe))
	}

	for _, obj := range []runtime.ApplyConfiguration{
		uiDeployment(cr, image, r.Config.ImagePullPolicy, searchpeURL),
		xhService(cr, name, xhUILabel, componentUI, "http", xhAppPort),
		podDisruptionBudget(cr, name, xhUILabel, componentUI),
	} {
		if err := r.apply(ctx, obj); err != nil {
			return false, err
		}
	}
	return true, nil
}

func (r *XHandlerReconciler) reconcileIngress(ctx context.Context, cr *xhandlerv1alpha1.XHandler) error {
	if !xhIngressEnabled(cr) {
		return r.deleteAll(ctx, &networkingv1.Ingress{ObjectMeta: objectMeta(cr, xhIngressName(cr))})
	}
	return r.apply(ctx, xhIngress(cr))
}

// reconcileMigration runs the migration Job of image, once: it is true when the Job has
// succeeded. A failed Job stays, for its logs, until it is deleted: then a new one runs.
func (r *XHandlerReconciler) reconcileMigration(ctx context.Context, cr *xhandlerv1alpha1.XHandler, image string) (bool, ctrl.Result, error) {
	name := xhMigrateJobName(cr, image)
	job := &batchv1.Job{}
	err := r.Get(ctx, client.ObjectKey{Namespace: cr.Namespace, Name: name}, job)
	switch {
	case apierrors.IsNotFound(err):
		// The template of a Job can't change: it is applied once
		if err := r.apply(ctx, migrateJob(cr, image, r.Config.ImagePullPolicy)); err != nil {
			return false, ctrl.Result{}, fmt.Errorf("migration job: %w", err)
		}
		r.migrating(cr, name)
		return false, ctrl.Result{RequeueAfter: migrationPollInterval}, nil
	case err != nil:
		return false, ctrl.Result{}, fmt.Errorf("migration job: %w", err)
	}

	switch {
	case jobCondition(job, batchv1.JobComplete):
		r.setCondition(cr, ConditionMigrated, metav1.ConditionTrue, "Succeeded", fmt.Sprintf("Job %s succeeded", name))
		return true, ctrl.Result{}, r.deleteOldMigrations(ctx, cr, name)
	case jobCondition(job, batchv1.JobFailed):
		message := fmt.Sprintf("Job %s failed: see its logs, fix the cause and delete it to run the migrations again", name)
		r.setCondition(cr, ConditionMigrated, metav1.ConditionFalse, "Failed", message)
		r.setCondition(cr, ConditionReady, metav1.ConditionFalse, "MigrationFailed", message)
		return false, ctrl.Result{}, nil
	default:
		r.migrating(cr, name)
		return false, ctrl.Result{RequeueAfter: migrationPollInterval}, nil
	}
}

func (r *XHandlerReconciler) migrating(cr *xhandlerv1alpha1.XHandler, job string) {
	message := fmt.Sprintf("Job %s is migrating the database", job)
	r.setCondition(cr, ConditionMigrated, metav1.ConditionFalse, "Progressing", message)
	r.setCondition(cr, ConditionReady, metav1.ConditionFalse, "Migrating", message)
}

// deleteOldMigrations deletes the migration Jobs of the previous images.
func (r *XHandlerReconciler) deleteOldMigrations(ctx context.Context, cr *xhandlerv1alpha1.XHandler, current string) error {
	jobs := &batchv1.JobList{}
	if err := r.List(ctx, jobs, client.InNamespace(cr.Namespace),
		client.MatchingLabels(xhSelector(cr, xhMigrateLabel, componentMigrate))); err != nil {
		return fmt.Errorf("migration jobs: %w", err)
	}
	for i := range jobs.Items {
		job := &jobs.Items[i]
		if job.Name == current || !metav1.IsControlledBy(job, cr) {
			continue
		}
		if err := r.Delete(ctx, job, client.PropagationPolicy(metav1.DeletePropagationBackground)); client.IgnoreNotFound(err) != nil {
			return fmt.Errorf("delete migration job %s: %w", job.Name, err)
		}
	}
	return nil
}

func jobCondition(job *batchv1.Job, conditionType batchv1.JobConditionType) bool {
	for _, condition := range job.Status.Conditions {
		if condition.Type == conditionType && condition.Status == corev1.ConditionTrue {
			return true
		}
	}
	return false
}

// deploymentAvailable is true when every replica of the current template is available.
func deploymentAvailable(deployment *appsv1.Deployment) bool {
	replicas := int32(1)
	if deployment.Spec.Replicas != nil {
		replicas = *deployment.Spec.Replicas
	}
	status := deployment.Status
	return status.ObservedGeneration >= deployment.Generation &&
		status.UpdatedReplicas == replicas &&
		status.AvailableReplicas == replicas &&
		status.Replicas == replicas
}

// SetupWithManager registers the reconciler, which watches the resources it creates, and the
// Searchpe resources the UIs refer to.
func (r *XHandlerReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&xhandlerv1alpha1.XHandler{}).
		Owns(&appsv1.Deployment{}).
		Owns(&corev1.Service{}).
		Owns(&corev1.ConfigMap{}).
		Owns(&batchv1.Job{}).
		Owns(&policyv1.PodDisruptionBudget{}).
		Owns(&networkingv1.Ingress{}).
		Owns(&networkingv1.NetworkPolicy{}).
		Watches(&v1alpha1.Searchpe{}, handler.EnqueueRequestsFromMapFunc(r.xhandlersOfSearchpe)).
		Named("xhandler").
		Complete(r)
}

// xhandlersOfSearchpe are the XHandlers whose UI refers to a Searchpe.
func (r *XHandlerReconciler) xhandlersOfSearchpe(ctx context.Context, searchpe client.Object) []ctrl.Request {
	list := &xhandlerv1alpha1.XHandlerList{}
	if err := r.List(ctx, list, client.InNamespace(searchpe.GetNamespace())); err != nil {
		ctrl.LoggerFrom(ctx).Error(err, "Unable to list the XHandlers of a Searchpe", "searchpe", searchpe.GetName())
		return nil
	}
	var requests []ctrl.Request
	for _, cr := range list.Items {
		if cr.Spec.UI != nil && cr.Spec.UI.SearchpeRef == searchpe.GetName() {
			requests = append(requests, ctrl.Request{NamespacedName: client.ObjectKeyFromObject(&cr)})
		}
	}
	return requests
}
