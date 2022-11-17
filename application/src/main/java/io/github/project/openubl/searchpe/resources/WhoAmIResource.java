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

import io.github.project.openubl.searchpe.dto.BasicUserDto;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.metrics.annotation.Counted;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Consumes("application/json")
@Produces("application/json")
@Path("/whoami")
public class WhoAmIResource {

    @Inject
    SecurityIdentity securityIdentity;

    @Authenticated
    @GET
    @Path("/")
    @Counted(name = "getCurrentUserChecks", description = "How many times the current user data was requested")
    public BasicUserDto getCurrentUser(@Context SecurityContext ctx) {
        String authenticationScheme = ctx.getAuthenticationScheme();

        Principal principal = securityIdentity.getPrincipal();

        String username = principal.getName();
        Set<String> roles = securityIdentity.getRoles();

        // Generate result
        BasicUserDto result = new BasicUserDto();
        result.setUsername(username);
        result.setPermissions(roles.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new)));

        BasicUserEntity.find("username", username).<BasicUserEntity>firstResultOptional()
                .ifPresent(entity -> {
                    result.setId(entity.id);
                    result.setFullName(entity.fullName);
                });

        return result;
    }

}
