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

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.github.project.openubl.operator.Config;
import io.github.project.openubl.operator.Constants;
import io.github.project.openubl.operator.ValueOrSecret;
import io.github.project.openubl.operator.controllers.SearchpeDistConfigurator;
import io.github.project.openubl.operator.utils.CRDUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class SearchpeDeployment extends CRUDKubernetesDependentResource<Deployment, Searchpe>
        implements Matcher<Deployment, Searchpe>, Condition<Deployment, Searchpe> {

    @Inject
    Config config;

    public SearchpeDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(Searchpe cr, Context<Searchpe> context) {
        SearchpeDistConfigurator distConfigurator = new SearchpeDistConfigurator(cr);
        return newDeployment(cr, context, distConfigurator);
    }

    @Override
    public Result<Deployment> match(Deployment actual, Searchpe cr, Context<Searchpe> context) {
        final var desiredSpec = cr.getSpec();
        final var container = actual.getSpec()
                .getTemplate().getSpec().getContainers()
                .stream()
                .findFirst();

        return Result.nonComputed(container
                .map(c -> c.getImage().equals(desiredSpec.getImage()))
                .orElse(false)
        );
    }

    @Override
    public boolean isMet(DependentResource<Deployment, Searchpe> dependentResource, Searchpe primary, Context<Searchpe> context) {
        return context.getSecondaryResource(Deployment.class)
                .map(deployment -> {
                    final var status = deployment.getStatus();
                    if (status != null) {
                        final var readyReplicas = status.getReadyReplicas();
                        return readyReplicas != null && readyReplicas >= 1;
                    }
                    return false;
                })
                .orElse(false);
    }

    @SuppressWarnings("unchecked")
    private Deployment newDeployment(Searchpe cr, Context<Searchpe> context, SearchpeDistConfigurator distConfigurator) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new DeploymentBuilder()
                .withNewMetadata()
                    .withName(getDeploymentName(cr))
                    .withNamespace(cr.getMetadata().getNamespace())
                    .withLabels(contextLabels)
                    .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(getDeploymentSpec(cr, context, distConfigurator))
                .build();
    }

    @SuppressWarnings("unchecked")
    private DeploymentSpec getDeploymentSpec(Searchpe cr, Context<Searchpe> context, SearchpeDistConfigurator distConfigurator) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        Map<String, String> selectorLabels = getDeploymentSelectorLabels(cr);

        String image = Optional.ofNullable(cr.getSpec().getImage()).orElse(config.searchpe().image());
        String imagePullPolicy = config.searchpe().imagePullPolicy();

        List<EnvVar> envVars = Stream.concat(
                getEnvVars(cr, config).stream(),
                distConfigurator.getAllEnvVars().stream()
        ).collect(Collectors.toList());
        List<Volume> volumes = distConfigurator.getAllVolumes();
        List<VolumeMount> volumeMounts = distConfigurator.getAllVolumeMounts();

        var tlsConfigured = SearchpeService.isTlsConfigured(cr);
        var protocol = !tlsConfigured ? "http" : "https";
        var port = SearchpeService.getServicePort(cr);

        var baseProbe = new ArrayList<>(List.of("curl", "--head", "--fail", "--silent"));
        if (tlsConfigured) {
            baseProbe.add("--insecure");
        }

        return new DeploymentSpecBuilder()
                .withReplicas(cr.getSpec().getInstances())
                .withSelector(new LabelSelectorBuilder()
                        .withMatchLabels(selectorLabels)
                        .build()
                )
                .withTemplate(new PodTemplateSpecBuilder()
                        .withNewMetadata()
                        .withLabels(selectorLabels)
                        .addToLabels(contextLabels)
                        .endMetadata()
                        .withSpec(new PodSpecBuilder()
                                .withRestartPolicy("Always")
                                .withTerminationGracePeriodSeconds(30L)
                                .withImagePullSecrets(cr.getSpec().getImagePullSecrets())
                                .withContainers(new ContainerBuilder()
                                        .withName(Constants.SEARCHPE_NAME)
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(envVars)
                                        .withPorts(
                                                new ContainerPortBuilder()
                                                        .withName("http")
                                                        .withProtocol("TCP")
                                                        .withContainerPort(8080)
                                                        .build(),
                                                new ContainerPortBuilder()
                                                        .withName("https")
                                                        .withProtocol("TCP")
                                                        .withContainerPort(8443)
                                                        .build()
                                        )
                                        .withReadinessProbe(new ProbeBuilder()
                                                .withHttpGet(new HTTPGetActionBuilder()
                                                        .withPath("/q/health/ready")
                                                        .withNewPort("http")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(20)
                                                .withPeriodSeconds(2)
                                                .withFailureThreshold(250)
                                                .build()
                                        )
                                        .withLivenessProbe(new ProbeBuilder()
                                                .withHttpGet(new HTTPGetActionBuilder()
                                                        .withPath("/q/health/live")
                                                        .withNewPort("http")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(20)
                                                .withPeriodSeconds(2)
                                                .withFailureThreshold(150)
                                                .build()
                                        )
                                        .withVolumeMounts(volumeMounts)
                                        .build()
                                )
                                .withVolumes(volumes)
                                .build()
                        )
                        .build()
                )
                .build();
    }

    private List<EnvVar> getEnvVars(Searchpe cr, Config config) {
        // default config values
        List<ValueOrSecret> serverConfig = Constants.DEFAULT_DIST_CONFIG.entrySet().stream()
                .map(e -> new ValueOrSecret(e.getKey(), e.getValue(), null))
                .collect(Collectors.toList());

        // merge with the CR; the values in CR take precedence
        if (cr.getSpec().getAdditionalOptions() != null) {
            serverConfig.removeAll(cr.getSpec().getAdditionalOptions());
            serverConfig.addAll(cr.getSpec().getAdditionalOptions());
        }

        // set env vars
        List<EnvVar> envVars = serverConfig.stream()
                .map(v -> {
                    String envValue = v.getValue();
                    EnvVarSource envValueFrom = new EnvVarSourceBuilder()
                            .withSecretKeyRef(v.getSecret())
                            .build();

                    return new EnvVarBuilder()
                            .withName(v.getName())
                            .withValue(v.getSecret() == null ? envValue : null)
                            .withValueFrom(v.getSecret() != null ? envValueFrom : null)
                            .build();
                })
                .collect(Collectors.toList());

        return envVars;
    }

    public static String getDeploymentName(Searchpe cr) {
        return cr.getMetadata().getName() + Constants.DEPLOYMENT_SUFFIX;
    }

    public static Map<String, String> getDeploymentSelectorLabels(Searchpe cr) {
        return Map.of(
                "openubl-operator/group", "web",
                "openubl-operator/name", cr.getMetadata().getName()
        );
    }
}
