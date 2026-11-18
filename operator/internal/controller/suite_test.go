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
	"fmt"
	"os"
	"path/filepath"
	"testing"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	clientgoscheme "k8s.io/client-go/kubernetes/scheme"
	"k8s.io/client-go/rest"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/envtest"

	"github.com/project-openubl/xhandler-service/operator/api/v1alpha1"
	xhandlerv1alpha1 "github.com/project-openubl/xhandler-service/operator/api/xhandler/v1alpha1"
)

// The API server of the tests, started by TestMain. It has no controllers: the tests reconcile
// by calling the reconciler, or by starting a manager.
var (
	restConfig *rest.Config
	k8sClient  client.Client
)

func TestMain(m *testing.M) {
	os.Exit(runTests(m))
}

func runTests(m *testing.M) int {
	env := &envtest.Environment{
		CRDDirectoryPaths:     []string{filepath.Join("..", "..", "config", "crd", "bases")},
		ErrorIfCRDPathMissing: true,
	}
	var err error
	restConfig, err = env.Start()
	if err != nil {
		fmt.Fprintln(os.Stderr, "Unable to start the API server (run the tests with make test):", err)
		return 1
	}
	defer func() { _ = env.Stop() }()

	scheme := runtime.NewScheme()
	if err := clientgoscheme.AddToScheme(scheme); err != nil {
		panic(err)
	}
	if err := v1alpha1.AddToScheme(scheme); err != nil {
		panic(err)
	}
	if err := xhandlerv1alpha1.AddToScheme(scheme); err != nil {
		panic(err)
	}
	k8sClient, err = client.New(restConfig, client.Options{Scheme: scheme})
	if err != nil {
		panic(err)
	}
	return m.Run()
}

func newReconciler() *SearchpeReconciler {
	return &SearchpeReconciler{
		Client: k8sClient,
		Config: Config{Image: "ghcr.io/project-openubl/xhandler-service/searchpe:nightly", ImagePullPolicy: corev1.PullAlways},
	}
}

// newNamespace creates a namespace for the test.
func newNamespace(t *testing.T) string {
	t.Helper()
	namespace := &corev1.Namespace{ObjectMeta: metav1.ObjectMeta{GenerateName: "test-"}}
	if err := k8sClient.Create(t.Context(), namespace); err != nil {
		t.Fatal(err)
	}
	return namespace.Name
}

func reconcile(t *testing.T, cr *v1alpha1.Searchpe) {
	t.Helper()
	request := ctrl.Request{NamespacedName: client.ObjectKeyFromObject(cr)}
	if _, err := newReconciler().Reconcile(t.Context(), request); err != nil {
		t.Fatalf("reconcile: %v", err)
	}
}

func get(ctx context.Context, t *testing.T, namespace, name string, obj client.Object) {
	t.Helper()
	if err := k8sClient.Get(ctx, client.ObjectKey{Namespace: namespace, Name: name}, obj); err != nil {
		t.Fatalf("get %s: %v", name, err)
	}
}
