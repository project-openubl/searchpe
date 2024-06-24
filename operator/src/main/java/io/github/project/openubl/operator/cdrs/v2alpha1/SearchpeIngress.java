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
package io.github.project.openubl.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.github.project.openubl.operator.Constants;
import io.github.project.openubl.operator.utils.CRDUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class SearchpeIngress extends CRUDKubernetesDependentResource<Ingress, Searchpe>
        implements Condition<Ingress, Searchpe> {

    @Inject
    KubernetesClient k8sClient;

    public SearchpeIngress() {
        super(Ingress.class);
    }

    @Override
    protected Ingress desired(Searchpe cr, Context<Searchpe> context) {
        boolean isIngressEnabled = cr.getSpec().getIngressSpec() != null && CRDUtils.getValueFromSubSpec(cr.getSpec().getIngressSpec(), SearchpeSpec.IngressSpec::isEnabled)
                .orElse(false);

        return newIngress(cr, context);
    }

    @Override
    public boolean isMet(DependentResource<Ingress, Searchpe> dependentResource, Searchpe cr, Context<Searchpe> context) {
        boolean isIngressEnabled = CRDUtils.getValueFromSubSpec(cr.getSpec().getIngressSpec(), SearchpeSpec.IngressSpec::isEnabled)
                .orElse(false);

        if (!isIngressEnabled) {
            return true;
        }

        return context.getSecondaryResource(Ingress.class)
                .map(in -> {
                    final var status = in.getStatus();
                    if (status != null) {
                        final var ingresses = status.getLoadBalancer().getIngress();
                        // only set the status if the ingress is ready to provide the info we need
                        return ingresses != null && !ingresses.isEmpty();
                    }
                    return false;
                })
                .orElse(false);
    }

    @SuppressWarnings("unchecked")
    private Ingress newIngress(Searchpe cr, Context<Searchpe> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        String hostname = getHostname(cr);

        var serviceName = SearchpeService.getServiceName(cr);
        var servicePort = SearchpeService.getServicePort(cr);
        var backendProtocol = (!SearchpeService.isTlsConfigured(cr)) ? "HTTP" : "HTTPS";

        String tlsSecretName = CRDUtils.getValueFromSubSpec(cr.getSpec().getHttpSpec(), SearchpeSpec.HttpSpec::getTlsSecret)
                .orElse(null);
        var tls = new IngressTLSBuilder()
                .withSecretName(tlsSecretName)
                .build();

        return new IngressBuilder()
                .withNewMetadata()
                    .withName(getIngressName(cr))
                    .withNamespace(cr.getMetadata().getNamespace())
                    .addToAnnotations("nginx.ingress.kubernetes.io/backend-protocol", backendProtocol)
                    .addToAnnotations("console.alpha.openshift.io/overview-app-route", "true")
                    .withLabels(labels)
                    .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withNewSpec()
                    .addNewRule()
                        .withHost(hostname)
                        .withNewHttp()
                            .addNewPath()
                                .withPath("/")
                                .withPathType("Prefix")
                                .withNewBackend()
                                    .withNewService()
                                        .withName(serviceName)
                                        .withNewPort()
                                            .withNumber(servicePort)
                                        .endPort()
                                    .endService()
                                .endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                    .withTls(tls)
                .endSpec()
                .build();
    }

    protected String getHostname(Searchpe cr) {
        return CRDUtils
                .getValueFromSubSpec(cr.getSpec().getHostnameSpec(), SearchpeSpec.HostnameSpec::getHostname)
                .orElseGet(() -> getClusterDomainOnOpenshift()
                        // Openshift
                        .map(domain -> CRDUtils
                                .getValueFromSubSpec(cr.getSpec().getHostnameSpec(), SearchpeSpec.HostnameSpec::getHostname)
                                .orElseGet(() -> k8sClient.getConfiguration().getNamespace() + "-" + cr.getMetadata().getName() + "." + domain)
                        )
                        // Kubernetes vanilla
                        .orElse(null)
                );
    }

    private Optional<String> getClusterDomainOnOpenshift() {
        String clusterDomain = null;
        try {
            CustomResourceDefinitionContext customResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
                    .withName("Ingress")
                    .withGroup("config.openshift.io")
                    .withVersion("v1")
                    .withPlural("ingresses")
                    .withScope("Cluster")
                    .build();
            GenericKubernetesResource clusterObject = k8sClient.genericKubernetesResources(customResourceDefinitionContext)
                    .withName("cluster")
                    .get();

            Map<String, String> objectSpec = Optional.ofNullable(clusterObject)
                    .map(kubernetesResource -> kubernetesResource.<Map<String, String>>get("spec"))
                    .orElse(Collections.emptyMap());
            clusterDomain = objectSpec.get("domain");
        } catch (KubernetesClientException exception) {
            // Nothing to do
            Log.info("No Openshift host found");
        }

        return Optional.ofNullable(clusterDomain);
    }

    public static Optional<String> getExposedURL(Searchpe cr, Ingress ingress) {
        final var status = ingress.getStatus();
        final var ingresses = status.getLoadBalancer().getIngress();
        Optional<IngressLoadBalancerIngress> ing = ingresses.isEmpty() ? Optional.empty() : Optional.of(ingresses.get(0));

        final var protocol = SearchpeService.isTlsConfigured(cr) ? "https" : "http";
        return ing.map(i -> protocol + "://" + (i.getHostname() != null ? i.getHostname() : i.getIp()));
    }

    public static String getIngressName(Searchpe cr) {
        return cr.getMetadata().getName() + Constants.INGRESS_SUFFIX;
    }

}
