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

// Package controller reconciles Searchpe resources into the Kubernetes resources that run it.
package controller

import (
	"context"
	"fmt"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/builder"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/log"

	"github.com/project-openubl/searchpe/operator/api/v1alpha1"
)

// fieldOwner owns the fields the operator applies. It is the field manager the Java operator
// used, so the fields it set belong to this operator after an upgrade.
const fieldOwner = client.FieldOwner("searchpe")

// Config is the configuration of the operator.
type Config struct {
	// Image is the Searchpe image of the resources that don't set one.
	Image string
	// ImagePullPolicy is the pull policy of the Searchpe image.
	ImagePullPolicy corev1.PullPolicy
}

// SearchpeReconciler runs each Searchpe with a Deployment, exposed by a Service and an Ingress.
type SearchpeReconciler struct {
	client.Client
	Config Config
}

// +kubebuilder:rbac:groups=searchpe.openubl.io,namespace=system,resources=searchpes,verbs=get;list;watch
// +kubebuilder:rbac:groups=searchpe.openubl.io,namespace=system,resources=searchpes/finalizers,verbs=update
// +kubebuilder:rbac:groups=apps,namespace=system,resources=deployments,verbs=get;list;watch;create;patch
// +kubebuilder:rbac:groups="",namespace=system,resources=secrets;services,verbs=get;list;watch;create;patch
// +kubebuilder:rbac:groups=networking.k8s.io,namespace=system,resources=ingresses,verbs=get;list;watch;create;patch;delete

// Reconcile brings the resources of a Searchpe to the state its spec describes.
func (r *SearchpeReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	cr := &v1alpha1.Searchpe{}
	if err := r.Get(ctx, req.NamespacedName, cr); err != nil {
		return ctrl.Result{}, client.IgnoreNotFound(err)
	}
	if !cr.DeletionTimestamp.IsZero() {
		// Kubernetes deletes the resources it owns
		return ctrl.Result{}, nil
	}

	if err := r.reconcileBasicAuthSecret(ctx, cr); err != nil {
		return ctrl.Result{}, fmt.Errorf("basic auth secret: %w", err)
	}
	if err := r.apply(ctx, deployment(cr, r.Config)); err != nil {
		return ctrl.Result{}, fmt.Errorf("deployment: %w", err)
	}
	if err := r.apply(ctx, service(cr)); err != nil {
		return ctrl.Result{}, fmt.Errorf("service: %w", err)
	}
	if err := r.reconcileIngress(ctx, cr); err != nil {
		return ctrl.Result{}, fmt.Errorf("ingress: %w", err)
	}
	return ctrl.Result{}, nil
}

func (r *SearchpeReconciler) apply(ctx context.Context, obj runtime.ApplyConfiguration) error {
	return r.Apply(ctx, obj, fieldOwner, client.ForceOwnership)
}

// reconcileBasicAuthSecret creates the secret with the session encryption key of basic auth, and
// applies its metadata. Its key never changes: a new key would log every user out.
func (r *SearchpeReconciler) reconcileBasicAuthSecret(ctx context.Context, cr *v1alpha1.Searchpe) error {
	existing := &metav1.PartialObjectMetadata{}
	existing.SetGroupVersionKind(corev1.SchemeGroupVersion.WithKind("Secret"))
	err := r.Get(ctx, client.ObjectKey{Namespace: cr.Namespace, Name: basicAuthSecretName(cr)}, existing)
	switch {
	case apierrors.IsNotFound(err):
		if err := r.Create(ctx, basicAuthSecret(cr)); client.IgnoreAlreadyExists(err) != nil {
			return err
		}
	case err != nil:
		return err
	}
	return r.apply(ctx, basicAuthSecretMetadata(cr))
}

// reconcileIngress applies the ingress, or deletes it when it is disabled, and logs the URL the
// ingress exposes Searchpe at once it has one.
func (r *SearchpeReconciler) reconcileIngress(ctx context.Context, cr *v1alpha1.Searchpe) error {
	if !ingressEnabled(cr) {
		disabled := &networkingv1.Ingress{ObjectMeta: metav1.ObjectMeta{Name: ingressName(cr), Namespace: cr.Namespace}}
		return client.IgnoreNotFound(r.Delete(ctx, disabled))
	}

	ingress := ingress(cr)
	if err := r.apply(ctx, ingress); err != nil {
		return err
	}
	if url := exposedURL(cr, ingress); url != "" {
		log.FromContext(ctx).Info("Searchpe is exposed", "url", url)
	}
	return nil
}

// SetupWithManager registers the reconciler, which watches the resources it creates. It caches
// only the metadata of the secrets: they hold the credentials of the namespace.
func (r *SearchpeReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&v1alpha1.Searchpe{}).
		Owns(&appsv1.Deployment{}).
		Owns(&corev1.Service{}).
		Owns(&networkingv1.Ingress{}).
		Owns(&corev1.Secret{}, builder.OnlyMetadata).
		Named("searchpe").
		Complete(r)
}
