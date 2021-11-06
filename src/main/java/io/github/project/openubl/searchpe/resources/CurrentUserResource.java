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

import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.quarkus.security.identity.SecurityIdentity;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.security.Principal;
import java.util.Set;

@Path("/current-user")
public class CurrentUserResource {

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Path("/whoami")
    @Produces("application/json")
    public BasicUserEntity getCurrentUser() {
        Principal principal = securityIdentity.getPrincipal();

        String username = principal.getName();
        Set<String> roles = securityIdentity.getRoles();

        // Generate result
        BasicUserEntity result = new BasicUserEntity();
        result.username = username;
        result.role = String.join(",", roles);
        return result;
    }

}
