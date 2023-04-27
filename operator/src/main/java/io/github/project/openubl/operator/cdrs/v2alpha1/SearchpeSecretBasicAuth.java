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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.github.project.openubl.operator.Constants;
import io.github.project.openubl.operator.utils.CRDUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class SearchpeSecretBasicAuth extends CRUDKubernetesDependentResource<Secret, Searchpe> implements Creator<Secret, Searchpe> {

    public SearchpeSecretBasicAuth() {
        super(Secret.class);
    }

    @Override
    protected Secret desired(Searchpe cr, Context<Searchpe> context) {
        return newIngress(cr, context);
    }

    @Override
    public Matcher.Result<Secret> match(Secret actual, Searchpe cr, Context<Searchpe> context) {
        final var desiredSecretName = getSecretName(cr);
        return Matcher.Result.nonComputed(actual.getMetadata().getName().equals(desiredSecretName));
    }

    @SuppressWarnings("unchecked")
    private Secret newIngress(Searchpe cr, Context<Searchpe> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        String encryptionKey = Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes());

        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName(getSecretName(cr))
                    .withNamespace(cr.getMetadata().getNamespace())
                    .withLabels(labels)
                    .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withData(Map.of(
                        Constants.BASIC_AUTH_SECRET_ENCRYPTIONKEY, encryptionKey
                ))
                .build();

        return secret;
    }

    public static String getSecretName(Searchpe cr) {
        return cr.getMetadata().getName() + Constants.BASIC_AUTH_SECRET_SUFFIX + Constants.SECRET_SUFFIX;
    }
}
