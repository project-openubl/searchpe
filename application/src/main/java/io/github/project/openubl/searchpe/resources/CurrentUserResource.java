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
import io.github.project.openubl.searchpe.dto.BasicUserPasswordChangeDto;
import io.github.project.openubl.searchpe.mapper.BasicUserMapper;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.github.project.openubl.searchpe.resources.interceptors.HTTPBasicAuthEnabled;
import io.github.project.openubl.searchpe.services.BasicUserService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.security.Principal;

@ApplicationScoped
@Consumes("application/json")
@Produces("application/json")
@Path("/current-user")
public class CurrentUserResource {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    BasicUserService basicUserService;

    @Inject
    BasicUserMapper basicUserMapper;

    @Transactional
    @Authenticated
    @HTTPBasicAuthEnabled
    @PUT
    @Path("/profile")
    public Response updateProfile(BasicUserDto rep) {
        Principal principal = securityIdentity.getPrincipal();
        String username = principal.getName();

        BasicUserEntity user = BasicUserEntity.find("username", username)
                .<BasicUserEntity>firstResultOptional()
                .orElseThrow(IllegalStateException::new);

        // To make sure the password is not changed
        rep.setPassword(null);
        rep.setPermissions(null);

        BasicUserEntity entity = basicUserService.update(user, rep);
        return Response
                .accepted(basicUserMapper.toDto(entity))
                .build();
    }

    @Transactional
    @Authenticated
    @HTTPBasicAuthEnabled
    @POST
    @Path("/credentials")
    public Response updateCurrentUserCredentials(BasicUserPasswordChangeDto rep) {
        Principal principal = securityIdentity.getPrincipal();
        String username = principal.getName();

        BasicUserEntity user = BasicUserEntity.find("username", username)
                .<BasicUserEntity>firstResultOptional()
                .orElseThrow(IllegalStateException::new);

        basicUserService.changePassword(user, rep);
        return Response
                .status(Response.Status.OK)
                .build();
    }
}
