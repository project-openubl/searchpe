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
import io.github.project.openubl.searchpe.dto.ErrorDto;
import io.github.project.openubl.searchpe.mapper.BasicUserMapper;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.github.project.openubl.searchpe.resources.interceptors.HTTPBasicAuthEnabled;
import io.github.project.openubl.searchpe.security.Permission;
import io.github.project.openubl.searchpe.services.BasicUserService;
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
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
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

    @Inject
    BasicUserService basicUserService;

    @Inject
    BasicUserMapper basicUserMapper;

    @RolesAllowed({Permission.admin, Permission.user_write})
    @HTTPBasicAuthEnabled
    @Operation(summary = "Create user", description = "Creates a new user")
    @POST
    @Path("/")
    public Response createUser(@NotNull @Valid BasicUserDto dto) {
        if (BasicUserEntity.find("username", dto.getUsername()).firstResultOptional().isPresent()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorDto.builder()
                            .message("Username already exists")
                            .build()
                    )
                    .build();
        }
        if (!dto.getPermissions()
                .stream()
                .allMatch(permission -> Permission.allPermissions
                        .stream()
                        .anyMatch(systemPermission -> Objects.equals(systemPermission, permission))
                )
        ) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorDto.builder()
                            .message("Invalid permissions")
                            .build())
                    .build();
        }

        BasicUserEntity entity = basicUserService.create(dto);
        return Response.status(Response.Status.CREATED)
                .entity(basicUserMapper.toDto(entity))
                .build();
    }

    @RolesAllowed({Permission.admin, Permission.user_write})
    @HTTPBasicAuthEnabled
    @Operation(summary = "Get users", description = "Get users")
    @GET
    public List<BasicUserDto> getUsers() {
        return BasicUserEntity.<BasicUserEntity>findAll().stream()
                .map(entity -> basicUserMapper.toDto(entity))
                .collect(Collectors.toList());
    }

    @RolesAllowed({Permission.admin, Permission.user_write})
    @HTTPBasicAuthEnabled
    @Operation(summary = "Get user", description = "Get user")
    @GET
    @Path("/{id}")
    public BasicUserDto getUser(@PathParam("id") Long id) {
        BasicUserEntity entity = (BasicUserEntity) BasicUserEntity.findByIdOptional(id).orElseThrow(NotFoundException::new);
        return basicUserMapper.toDto(entity);
    }

    @RolesAllowed({Permission.admin, Permission.user_write})
    @HTTPBasicAuthEnabled
    @Operation(summary = "Update user", description = "Update username or password. It won't update current role")
    @PUT
    @Path("/{id}")
    public Response updateUser(@NotNull @PathParam("id") Long id, @NotNull BasicUserDto dto) {
        BasicUserEntity user = (BasicUserEntity) BasicUserEntity.findByIdOptional(id).orElseThrow(NotFoundException::new);

        // Doing this for making the validator pass
        boolean tempPasswordSet = false;
        if (dto.getPassword() == null) {
            dto.setPassword("123456789");
            tempPasswordSet = true;
        }

        Set<ConstraintViolation<BasicUserDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("The info sent is not valid").build();
        }

        // Restore password
        if (tempPasswordSet) {
            dto.setPassword(null);
        }

        BasicUserEntity entity = basicUserService.update(user, dto);
        return Response
                .accepted(basicUserMapper.toDto(entity))
                .build();
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
