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

import io.github.project.openubl.searchpe.models.RoleType;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.github.project.openubl.searchpe.resources.interceptors.HTTPBasicAuthEnabled;
import org.eclipse.microprofile.openapi.annotations.Operation;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Transactional
@ApplicationScoped
@Path("/admin/users")
@Consumes("application/json")
@Produces("application/json")
public class BasicUserResource {

    private BasicUserEntity toDTO(BasicUserEntity entity) {
        BasicUserEntity result = new BasicUserEntity();
        result.id = entity.id;
        result.username = entity.username;
        result.role = entity.role;
        return result;
    }

    @RolesAllowed("admin")
    @HTTPBasicAuthEnabled
    @Operation(summary = "Create user", description = "Creates a new user")
    @POST
    @Path("/")
    public Response createUser(@NotNull @Valid BasicUserEntity user) {
        if (Objects.isNull(user.role)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Can not create user without role")
                    .build();
        }

        if (user.role.equals(RoleType.admin.toString())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Only one admin user can exist")
                    .build();
        }

        BasicUserEntity.add(user.username, user.password, user.role);
        return Response.status(Response.Status.CREATED).build();
    }

    @RolesAllowed("admin")
    @HTTPBasicAuthEnabled
    @Operation(summary = "Get users", description = "Get users")
    @GET
    public List<BasicUserEntity> getUsers() {
        return BasicUserEntity.findAll().stream()
                .map(f -> toDTO((BasicUserEntity) f))
                .collect(Collectors.toList());
    }

    @RolesAllowed("admin")
    @HTTPBasicAuthEnabled
    @Operation(summary = "Get user", description = "Get user")
    @GET
    @Path("/{id}")
    public BasicUserEntity getUser(@PathParam("id") Long id) {
        BasicUserEntity user = (BasicUserEntity) BasicUserEntity.findByIdOptional(id).orElseThrow(NotFoundException::new);
        return toDTO(user);
    }

    @RolesAllowed("admin")
    @HTTPBasicAuthEnabled
    @Operation(summary = "Update user", description = "Update username or password. It won't update current role")
    @PUT
    @Path("/{id}")
    public void updateUser(@NotNull @PathParam("id") Long id, @NotNull @Valid BasicUserEntity rep) {
        BasicUserEntity user = (BasicUserEntity) BasicUserEntity.findByIdOptional(id).orElseThrow(NotFoundException::new);
        BasicUserEntity.update(user, rep.username, rep.password);
    }

    @RolesAllowed("admin")
    @HTTPBasicAuthEnabled
    @Operation(summary = "Delete user", description = "Delete user")
    @DELETE
    @Path("/{id}")
    public Response deleteUser(@NotNull @PathParam("id") Long id) {
        Optional<BasicUserEntity> userOptional = BasicUserEntity.findByIdOptional(id);
        if (userOptional.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .build();
        }

        BasicUserEntity user = userOptional.get();
        if (Objects.equals(user.role, RoleType.admin.toString())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Admin user can not be deleted")
                    .build();
        }

        user.delete();
        return Response.status(Response.Status.OK).build();
    }
}
