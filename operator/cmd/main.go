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

// The searchpe operator.
//
// Environment:
//   - RELATED_IMAGE_SEARCHPE (required): the Searchpe image of the resources that don't set one.
//   - RELATED_IMAGE_XHANDLER, RELATED_IMAGE_XHANDLER_ENGINE, RELATED_IMAGE_XHANDLER_UI and
//     RELATED_IMAGE_EGRESS_PROXY: the xhandler-service, engine, UI and egress proxy images of the
//     XHandler resources that don't set one. An XHandler that needs a missing one isn't deployed:
//     its Ready condition says why.
//   - SEARCHPE_IMAGE_PULL_POLICY: the pull policy of the Searchpe and xhandler images, Always by
//     default. The egress proxy image is pulled if not present.
//   - WATCH_NAMESPACE: the namespace whose resources it reconciles. Every namespace when empty,
//     which needs the permissions of its role in every namespace.
package main

import (
	"flag"
	"os"

	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/runtime"
	clientgoscheme "k8s.io/client-go/kubernetes/scheme"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/cache"
	"sigs.k8s.io/controller-runtime/pkg/healthz"
	"sigs.k8s.io/controller-runtime/pkg/log/zap"
	metricsserver "sigs.k8s.io/controller-runtime/pkg/metrics/server"

	"github.com/project-openubl/searchpe/operator/api/v1alpha1"
	xhandlerv1alpha1 "github.com/project-openubl/searchpe/operator/api/xhandler/v1alpha1"
	"github.com/project-openubl/searchpe/operator/internal/controller"
)

// Leader election:
// +kubebuilder:rbac:groups=coordination.k8s.io,namespace=system,resources=leases,verbs=get;list;watch;create;update;patch;delete
// +kubebuilder:rbac:groups="",namespace=system,resources=events,verbs=create;patch

func main() {
	var metricsAddr, probeAddr string
	var leaderElect bool
	flag.StringVar(&metricsAddr, "metrics-bind-address", "0", "The address of the metrics endpoint, 0 to disable it.")
	flag.StringVar(&probeAddr, "health-probe-bind-address", ":8081", "The address of the health probes.")
	flag.BoolVar(&leaderElect, "leader-elect", false, "Run one active operator at a time, with leader election.")
	zapOptions := zap.Options{}
	zapOptions.BindFlags(flag.CommandLine)
	flag.Parse()
	ctrl.SetLogger(zap.New(zap.UseFlagOptions(&zapOptions)))
	log := ctrl.Log.WithName("setup")

	config := controller.Config{
		Image:           os.Getenv("RELATED_IMAGE_SEARCHPE"),
		ImagePullPolicy: corev1.PullPolicy(os.Getenv("SEARCHPE_IMAGE_PULL_POLICY")),
	}
	if config.Image == "" {
		log.Error(nil, "RELATED_IMAGE_SEARCHPE is not set")
		os.Exit(1)
	}
	switch config.ImagePullPolicy {
	case "":
		config.ImagePullPolicy = corev1.PullAlways
	case corev1.PullAlways, corev1.PullIfNotPresent, corev1.PullNever:
	default:
		log.Error(nil, "SEARCHPE_IMAGE_PULL_POLICY must be Always, IfNotPresent or Never", "value", config.ImagePullPolicy)
		os.Exit(1)
	}

	xhandlerConfig := controller.XHandlerConfig{
		Image:            os.Getenv("RELATED_IMAGE_XHANDLER"),
		EngineImage:      os.Getenv("RELATED_IMAGE_XHANDLER_ENGINE"),
		UIImage:          os.Getenv("RELATED_IMAGE_XHANDLER_UI"),
		EgressProxyImage: os.Getenv("RELATED_IMAGE_EGRESS_PROXY"),
		ImagePullPolicy:  config.ImagePullPolicy,
	}

	scheme := runtime.NewScheme()
	if err := clientgoscheme.AddToScheme(scheme); err != nil {
		log.Error(err, "Unable to register the Kubernetes types")
		os.Exit(1)
	}
	if err := v1alpha1.AddToScheme(scheme); err != nil {
		log.Error(err, "Unable to register the Searchpe types")
		os.Exit(1)
	}
	if err := xhandlerv1alpha1.AddToScheme(scheme); err != nil {
		log.Error(err, "Unable to register the XHandler types")
		os.Exit(1)
	}

	options := ctrl.Options{
		Scheme:                 scheme,
		Metrics:                metricsserver.Options{BindAddress: metricsAddr},
		HealthProbeBindAddress: probeAddr,
		LeaderElection:         leaderElect,
		LeaderElectionID:       "searchpe-operator.searchpe.openubl.io",
		// The operator stops right after the manager: the next one can lead at once
		LeaderElectionReleaseOnCancel: true,
	}
	// The XHandler controller owns ConfigMaps, Jobs and NetworkPolicies: only its own are cached
	options.Cache.ByObject = controller.XHandlerCacheByObject()
	if namespace := os.Getenv("WATCH_NAMESPACE"); namespace != "" {
		options.Cache.DefaultNamespaces = map[string]cache.Config{namespace: {}}
	}

	mgr, err := ctrl.NewManager(ctrl.GetConfigOrDie(), options)
	if err != nil {
		log.Error(err, "Unable to create the manager")
		os.Exit(1)
	}
	reconciler := &controller.SearchpeReconciler{Client: mgr.GetClient(), Config: config}
	if err := reconciler.SetupWithManager(mgr); err != nil {
		log.Error(err, "Unable to create the Searchpe controller")
		os.Exit(1)
	}
	xhandlerReconciler := &controller.XHandlerReconciler{Client: mgr.GetClient(), Config: xhandlerConfig}
	if err := xhandlerReconciler.SetupWithManager(mgr); err != nil {
		log.Error(err, "Unable to create the XHandler controller")
		os.Exit(1)
	}
	if err := mgr.AddHealthzCheck("healthz", healthz.Ping); err != nil {
		log.Error(err, "Unable to set up the health check")
		os.Exit(1)
	}
	if err := mgr.AddReadyzCheck("readyz", healthz.Ping); err != nil {
		log.Error(err, "Unable to set up the ready check")
		os.Exit(1)
	}

	log.Info("Starting the manager")
	if err := mgr.Start(ctrl.SetupSignalHandler()); err != nil {
		log.Error(err, "The manager failed")
		os.Exit(1)
	}
}
