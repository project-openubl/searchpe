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
	"testing"

	networkingv1ac "k8s.io/client-go/applyconfigurations/networking/v1"

	"github.com/project-openubl/searchpe/operator/api/v1alpha1"
)

func TestExposedURL(t *testing.T) {
	withTLS := &v1alpha1.Searchpe{Spec: v1alpha1.SearchpeSpec{HTTP: &v1alpha1.HTTPSpec{TLSSecret: "tls"}}}
	for _, test := range []struct {
		name      string
		cr        *v1alpha1.Searchpe
		addresses []*networkingv1ac.IngressLoadBalancerIngressApplyConfiguration
		want      string
	}{
		{name: "no address yet", cr: &v1alpha1.Searchpe{}},
		{
			name:      "ip",
			cr:        &v1alpha1.Searchpe{},
			addresses: []*networkingv1ac.IngressLoadBalancerIngressApplyConfiguration{networkingv1ac.IngressLoadBalancerIngress().WithIP("10.0.0.1")},
			want:      "http://10.0.0.1",
		},
		{
			name:      "hostname, with TLS",
			cr:        withTLS,
			addresses: []*networkingv1ac.IngressLoadBalancerIngressApplyConfiguration{networkingv1ac.IngressLoadBalancerIngress().WithHostname("searchpe.example.com").WithIP("10.0.0.1")},
			want:      "https://searchpe.example.com",
		},
	} {
		t.Run(test.name, func(t *testing.T) {
			ingress := networkingv1ac.Ingress("searchpe", "default").
				WithStatus(networkingv1ac.IngressStatus().
					WithLoadBalancer(networkingv1ac.IngressLoadBalancerStatus().WithIngress(test.addresses...)))
			if got := exposedURL(test.cr, ingress); got != test.want {
				t.Errorf("exposedURL() = %q, want %q", got, test.want)
			}
		})
	}
}
