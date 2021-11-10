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
import io.github.project.openubl.searchpe.idm.ErrorRepresentation;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.github.project.openubl.searchpe.resources.interceptors.HTTPBasicAuthEnabled;
import io.github.project.openubl.searchpe.security.Permission;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.logging.Logger;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.ConstraintViolation;
import javax.validation.Valid;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Transactional
@ApplicationScoped
@Path("/admin/users")
@Consumes("application/json")
@Produces("application/json")
public class BasicUserResource {

    private static final Logger LOGGER = Logger.getLogger(BasicUserResource.class);

    @Inject
    Validator validator;

    @RolesAllowed({Permission.admin, Permission.user_write})
    @HTTPBasicAuthEnabled
    @Operation(summary = "Create user", description = "Creates a new user")
    @POST
    @Path("/")
    public Response createUser(@NotNull @Valid BasicUserRepresentation rep) {
        if (BasicUserEntity.find("username", rep.getUsername()).firstResultOptional().isPresent()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorRepresentation("Username already exists"))
                    .build();
        }
        if (!rep.getPermissions()
                .stream()
                .allMatch(permission -> Permission.allPermissions
                        .stream()
                        .anyMatch(systemPermission -> Objects.equals(systemPermission, permission))
                )
        ) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorRepresentation("Invalid permissions"))
                    .build();
        }

        BasicUserEntity userEntity = BasicUserEntity.add(rep);
        return Response.status(Response.Status.CREATED)
                .entity(userEntity.toRepresentation())
                .build();
    }

    @RolesAllowed({Permission.admin, Permission.user_write})
    @HTTPBasicAuthEnabled
    @Operation(summary = "Get users", description = "Get users")
    @GET
    public List<BasicUserRepresentation> getUsers() {
        return BasicUserEntity.findAll().stream()
                .map(f -> ((BasicUserEntity) f).toRepresentation())
                .collect(Collectors.toList());
    }

    @RolesAllowed({Permission.admin, Permission.user_write})
    @HTTPBasicAuthEnabled
    @Operation(summary = "Get user", description = "Get user")
    @GET
    @Path("/{id}")
    public BasicUserRepresentation getUser(@PathParam("id") Long id) {
        BasicUserEntity user = (BasicUserEntity) BasicUserEntity.findByIdOptional(id).orElseThrow(NotFoundException::new);
        return user.toRepresentation();
    }

    @RolesAllowed({Permission.admin, Permission.user_write})
    @HTTPBasicAuthEnabled
    @Operation(summary = "Update user", description = "Update username or password. It won't update current role")
    @PUT
    @Path("/{id}")
    public Response updateUser(@NotNull @PathParam("id") Long id, @NotNull BasicUserRepresentation rep) {
        BasicUserEntity user = (BasicUserEntity) BasicUserEntity.findByIdOptional(id).orElseThrow(NotFoundException::new);

        // Doing this for making the validator pass
        boolean tempPasswordSet = false;
        if (rep.getPassword() == null) {
            rep.setPassword("123456789");
            tempPasswordSet = true;
        }

        Set<ConstraintViolation<BasicUserRepresentation>> violations = validator.validate(rep);
        if (!violations.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("The info sent is not valid").build();
        }

        // Restore password
        if (tempPasswordSet) {
            rep.setPassword(null);
        }

        BasicUserEntity update = BasicUserEntity.update(user, rep);
        return Response.accepted(update.toRepresentation()).build();
    }

    @RolesAllowed({Permission.admin, Permission.user_write})
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
        user.delete();
        return Response.status(Response.Status.OK).build();
    }

}
