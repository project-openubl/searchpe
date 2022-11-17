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
package io.github.project.openubl.operator;

import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;
import io.quarkiverse.operatorsdk.bundle.runtime.SharedCSVMetadata;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@CSVMetadata(
//        name = "searchpe-operator.v${RELEASE_VERSION}",
//        version = "${RELEASE_VERSION}",
        annotations = @CSVMetadata.Annotations(
                containerImage = "projectopenubl/searchpe-operator/${RELEASE_VERSION}",
                repository = "https://github.com/project-openubl/searchpe",
                categories = "Application Runtime",
                capabilities = "Basic Install",
                almExamples = """
                        kind: "Searchpe"
                        apiVersion: "searchpe.openubl.io/v1alpha1"
                        metadata:
                          name: searchpe
                        spec:
                          db:
                            usernameSecret:
                              name: postgresql-pguser-foo
                              key: user
                            passwordSecret:
                              name: postgresql-pguser-foo
                              key: password
                            url: jdbc:postgresql://postgresql-primary.default.svc:5432/searchpedb
                        """
        ),
        permissionRules = {
                @CSVMetadata.PermissionRule(
                        apiGroups = {""},
                        resources = {"pods", "services", "services/finalizers", "endpoints", "persistentvolumeclaims", "events", "configmaps", "secrets"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"route.openshift.io"},
                        resources = {"routes"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"networking.k8s.io"},
                        resources = {"ingresses", "networkpolicies"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"apps"},
                        resources = {"deployments"},
                        verbs = {"*"}
                )
        },
        displayName = "Searchpe Operator",
        description = """
                Searchpe is an enterprise solution for managing natural and legal entities in your organization. Valid for Peru.
                                
                                
                Searchpe is a project within the [Openubl community](https://project-openubl.github.io/).
                                
                                
                ### Install
                Once you have successfully installed the Operator, proceed to deploy components by creating the required Searchpe CR.
                                
                By default, the Operator installs the following components on a target cluster:
                                
                * Server, to manage the natual and legal entities.
                * UI, the web console to manage the application.
                                
                ### Documentation
                Documentation can be found on our [website](https://project-openubl.github.io/).
                                
                ### Getting help
                If you encounter any issues while using Searchpe operator, you can create an issue on our [Github repo](https://github.com/project-openubl/searchpe/issues), for bugs, enhancements or other requests.
                                
                ### Contributing
                You can contribute by:
                                
                * Raising any issues you find using Searchpe Operator
                * Fixing issues by opening [Pull Requests](https://github.com/project-openubl/searchpe/pulls)
                                
                """,
        installModes = {
                @CSVMetadata.InstallMode(type = "OwnNamespace", supported = true),
                @CSVMetadata.InstallMode(type = "SingleNamespace", supported = false),
                @CSVMetadata.InstallMode(type = "MultiNamespace", supported = false),
                @CSVMetadata.InstallMode(type = "AllNamespaces", supported = false)
        },
        keywords = {"sunat", "openubl", "searchpe"},
        maturity = "alpha",
        provider = @CSVMetadata.Provider(name = "OpenUbl"),
        links = {
                @CSVMetadata.Link(name = "Website", url = "https://project-openubl.github.io/"),
                @CSVMetadata.Link(name = "Github", url = "https://github.com/project-openubl/searchpe")
        },
        icon = @CSVMetadata.Icon(fileName = "icon.png", mediatype = "image/png"),
        maintainers = {@CSVMetadata.Maintainer(name = "OpenUbl", email = "projectopenubl+subscribe@googlegroups.com")}
)
public class SearchpeOperatorCSVMetadata implements SharedCSVMetadata {

    @ConfigProperty(name = "quarkus.application.version")
    String applicationVersion;

    public String getVersion() {
        return applicationVersion;
    }
}
