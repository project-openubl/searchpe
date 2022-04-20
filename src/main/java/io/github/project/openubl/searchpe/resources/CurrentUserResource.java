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

import io.github.project.openubl.searchpe.idm.BasicUserPasswordChangeRepresentation;
import io.github.project.openubl.searchpe.idm.BasicUserRepresentation;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.github.project.openubl.searchpe.resources.interceptors.HTTPBasicAuthEnabled;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.metrics.annotation.Counted;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.ConstraintViolation;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Consumes("application/json")
@Produces("application/json")
@Path("/current-user")
public class CurrentUserResource {

    @Inject
    SecurityIdentity securityIdentity;

    @Authenticated
    @GET
    @Path("/whoami")
    @Counted(name = "getCurrentUserChecks", description = "How many times the current user data was requested")
    public BasicUserRepresentation getCurrentUser() {
        Principal principal = securityIdentity.getPrincipal();

        String username = principal.getName();
        Set<String> roles = securityIdentity.getRoles();

        // Generate result
        BasicUserRepresentation result = new BasicUserRepresentation();
        result.setUsername(username);
        result.setPermissions(roles.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new)));

        BasicUserEntity.find("username", username).<BasicUserEntity>firstResultOptional()
                .ifPresent(entity -> {
                    result.setId(entity.id);
                    result.setFullName(entity.fullName);
                });

        return result;
    }

    @Transactional
    @Authenticated
    @HTTPBasicAuthEnabled
    @PUT
    @Path("/profile")
    public Response updateProfile(BasicUserRepresentation rep) {
        Principal principal = securityIdentity.getPrincipal();
        String username = principal.getName();

        BasicUserEntity user = BasicUserEntity.find("username", username)
                .<BasicUserEntity>firstResultOptional()
                .orElseThrow(IllegalStateException::new);

        // To make sure the password is not changed
        rep.setPassword(null);
        rep.setPermissions(null);

        BasicUserEntity update = BasicUserEntity.update(user, rep);
        return Response.accepted(update.toRepresentation()).build();
    }

    @Transactional
    @Authenticated
    @HTTPBasicAuthEnabled
    @POST
    @Path("/credentials")
    public Response updateCurrentUserCredentials(BasicUserPasswordChangeRepresentation rep) {
        Principal principal = securityIdentity.getPrincipal();
        String username = principal.getName();

        BasicUserEntity user = BasicUserEntity.find("username", username)
                .<BasicUserEntity>firstResultOptional()
                .orElseThrow(IllegalStateException::new);

        BasicUserEntity.changePassword(user, rep);
        return Response
                .status(Response.Status.OK)
                .build();
    }
}
