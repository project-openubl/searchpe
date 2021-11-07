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

import io.github.project.openubl.searchpe.idm.BasicUserRepresentation;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/current-user")
public class CurrentUserResource {

    @Inject
    SecurityIdentity securityIdentity;

    @Authenticated
    @GET
    @Path("/whoami")
    @Produces("application/json")
    public BasicUserRepresentation getCurrentUser() {
        Principal principal = securityIdentity.getPrincipal();

        String username = principal.getName();
        Set<String> roles = securityIdentity.getRoles();

        // Generate result
        BasicUserRepresentation result = new BasicUserRepresentation();
        result.setUsername(username);
        result.setPermissions(roles.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new)));
        return result;
    }

}
