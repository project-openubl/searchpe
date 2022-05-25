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
package io.github.project.openubl.searchpe.resources;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.Optional;

@Path("/templates")
public class FrontendResource {

    @Inject
    @Location("settings.js")
    Template settingsJS;

    @ConfigProperty(name = "searchpe.disable.authorization")
    Optional<Boolean> disableAuthorization;

    @ConfigProperty(name = "searchpe.allow.advancedSearch")
    Optional<Boolean> allowAdvancedSearch;

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled")
    Optional<Boolean> isOidcTenantEnabled;

    @ConfigProperty(name = "quarkus.http.auth.form.cookie-name")
    Optional<String> formCookieName;

    @ConfigProperty(name = "quarkus.oidc.logout.path")
    Optional<String> oidcLogoutPath;

    @ConfigProperty(name = "quarkus.application.version")
    Optional<String> applicationVersion;

    @Authenticated
    @GET
    @Path("/settings.js")
    @Produces("text/javascript")
    public TemplateInstance getSettingsJS() {
        String defaultAuthMethod;
        if (disableAuthorization.orElse(false)) {
            defaultAuthMethod = "none";
        } else if (isOidcTenantEnabled.orElse(false)) {
            defaultAuthMethod = "oidc";
        } else {
            defaultAuthMethod = "basic";
        }

        return settingsJS
                .data("defaultAuthMethod", defaultAuthMethod)
                .data("formCookieName", formCookieName.orElse(""))
                .data("oidcLogoutPath", oidcLogoutPath.orElse(""))
                .data("isAdvancedSearchEnabled", allowAdvancedSearch.orElse(false))
                .data("applicationVersion", applicationVersion.orElse(""));
    }

}
