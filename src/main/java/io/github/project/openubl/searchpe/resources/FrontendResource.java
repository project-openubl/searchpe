/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Eclipse Public License - v 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.models.jpa.search.SearchpeNoneIndexer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.Objects;
import java.util.Optional;

@Path("/templates")
public class FrontendResource {

    @Inject
    @Location("settings.js")
    Template settingsJS;

    @Inject
    @Location("keycloak.json")
    Template keycloakJSON;

    @ConfigProperty(name = "quarkus.oidc.enabled")
    Optional<Boolean> isOidcEnabled;

    @ConfigProperty(name = "quarkus.http.auth.form.cookie-name")
    Optional<String> formCookieName;

    @ConfigProperty(name = "quarkus.hibernate-search-orm.automatic-indexing.synchronization.strategy")
    Optional<String> esSyncStrategy;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    Optional<String> oidcServerUrl;

    @ConfigProperty(name = "quarkus.oidc.client-id")
    Optional<String> oidcClientId;

    @PermitAll
    @GET
    @Path("/settings.js")
    @Produces("text/javascript")
    public TemplateInstance getSettingsJS() {
        return settingsJS
                .data("defaultAuthMethod", isOidcEnabled.isPresent() && isOidcEnabled.get() ? "oidc" : "basic")
                .data("formCookieName", formCookieName.orElse(""))
                .data("isElasticsearchEnabled", !Objects.equals(esSyncStrategy.orElse(""), SearchpeNoneIndexer.BEAN_FULL_NAME));
    }

    @PermitAll
    @GET
    @Path("/keycloak.json")
    @Produces("application/json")
    public TemplateInstance getKeycloakJSON() {
        String realmName = "";
        String serverUrl = "";

        if (oidcServerUrl.isPresent()) {
            realmName = oidcServerUrl.get().substring(oidcServerUrl.get().lastIndexOf("/") + 1);
            serverUrl = oidcServerUrl.get().substring(0, oidcServerUrl.get().indexOf("/realms"));
        }

        return keycloakJSON
                .data("oidcRealm", realmName)
                .data("oidcServerUrl", serverUrl)
                .data("oidcResource", "searchpe-ui");
    }
}
