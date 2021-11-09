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

import io.github.project.openubl.searchpe.AbstractBaseTest;
import io.github.project.openubl.searchpe.StandaloneProfileManager;
import io.github.project.openubl.searchpe.idm.BasicUserRepresentation;
import io.github.project.openubl.searchpe.security.Permission;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(StandaloneProfileManager.class)
@TestHTTPEndpoint(BasicUserResource.class)
public class UserResource_StandaloneTest extends AbstractBaseTest {

    @Override
    public Class<?> getTestClass() {
        return UserResource_StandaloneTest.class;
    }

    @Test
    public void resourceNotAvailableForNotAdmins() {
        BasicUserRepresentation user = new BasicUserRepresentation();
        user.setFullName("newFullName");
        user.setUsername("newUser");
        user.setPassword("newPassword");
        user.setPermissions(new HashSet<>(List.of(Permission.search)));

        // Create
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .body(user)
                .post("/")
                .then()
                .statusCode(403);

        // Update
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .body(user)
                .put("/1")
                .then()
                .statusCode(403);

        // Get all
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(403);

        // Get one
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/1")
                .then()
                .statusCode(403);

        // Delete
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .delete("/1")
                .then()
                .statusCode(403);
    }

    @Test
    public void getDefaultUsers() {
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(2),
                        "[0].id", is(notNullValue()),
                        "[0].username", is("admin"),
                        "[0].password", is(nullValue()),
                        "[0].permissions", is(List.of("admin:app")),

                        "[1].id", is(notNullValue()),
                        "[1].username", is("alice"),
                        "[1].password", is(nullValue()),
                        "[1].permissions", is(List.of("search", "version:write"))
                );
    }

    @Test
    public void getOneUser() {
        BasicUserRepresentation[] users = givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .extract().body().as(BasicUserRepresentation[].class);

        assertEquals(2, users.length);
        BasicUserRepresentation userToGet = users[0];

        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/" + userToGet.getId())
                .then()
                .statusCode(200)
                .body(
                        "id", is(userToGet.getId().intValue()),
                        "username", is(userToGet.getUsername()),
                        "password", is(nullValue()),
                        "permissions", is(List.of(userToGet.getPermissions().toArray()))
                );
    }

    @Test
    public void updateUser() {
        // Given
        BasicUserRepresentation[] users = givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .extract().body().as(BasicUserRepresentation[].class);

        Optional<BasicUserRepresentation> userToUpdateOptional = Stream.of(users).filter(f -> f.getUsername().equals("alice")).findFirst();
        assertTrue(userToUpdateOptional.isPresent());

        BasicUserRepresentation userToUpdate = userToUpdateOptional.get();
        userToUpdate.setUsername("newUsername");
        userToUpdate.setPassword("newPassword");

        // When
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .body(userToUpdate)
                .put("/" + userToUpdate.getId())
                .then()
                .statusCode(202);

        // Then
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/" + userToUpdate.getId())
                .then()
                .statusCode(200)
                .body(
                        "id", is(userToUpdate.getId().intValue()),
                        "username", is(userToUpdate.getUsername()),
                        "password", is(nullValue()),
                        "permissions", is(notNullValue())
                );
    }

    @Test
    public void deleteUser() {
        // Given
        BasicUserRepresentation[] users = givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .extract().body().as(BasicUserRepresentation[].class);

        Optional<BasicUserRepresentation> userToDeleteOptional = Stream.of(users).filter(f -> f.getUsername().equals("alice")).findFirst();
        assertTrue(userToDeleteOptional.isPresent());
        BasicUserRepresentation userToDelete = userToDeleteOptional.get();

        // When
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .delete("/" + userToDelete.getId())
                .then()
                .statusCode(200);

        // Then
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/" + userToDelete.getId())
                .then()
                .statusCode(404);
    }

//    @Test
//    public void deleteAdminUserNotAllowed() {
//        // Given
//        BasicUserEntity[] users = givenAuth("admin")
//                .header("Content-Type", "application/json")
//                .when()
//                .get("/")
//                .then()
//                .statusCode(200)
//                .extract().body().as(BasicUserEntity[].class);
//
//        Optional<BasicUserEntity> userToDeleteOptional = Stream.of(users).filter(f -> f.permissions.equals(RoleType.admin.toString())).findFirst();
//        assertTrue(userToDeleteOptional.isPresent());
//        BasicUserEntity userToDelete = userToDeleteOptional.get();
//
//        // When
//        givenAuth("admin")
//                .header("Content-Type", "application/json")
//                .when()
//                .delete("/" + userToDelete.id)
//                .then()
//                .statusCode(400);
//    }

    @Test
    public void createUser() {
        // Given
        BasicUserRepresentation user = new BasicUserRepresentation();
        user.setFullName("myFullName");
        user.setUsername("myUsername");
        user.setPassword("myPassword");
        user.setPermissions(new HashSet<>(List.of(Permission.search)));

        // When
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .body(user)
                .post("/")
                .then()
                .statusCode(201)
                .body(
                        "fullName", is(user.getFullName()),
                        "username", is(user.getUsername()),
                        "password", is(nullValue()),
                        "permissions", is(List.of(user.getPermissions().toArray()))
                );
    }

    @Test
    public void createDuplicateUserNotAllowed() {
        // Given
        BasicUserRepresentation user = new BasicUserRepresentation();
        user.setFullName("myFullName");
        user.setUsername("myUsername");
        user.setPassword("myPassword");
        user.setPermissions(new HashSet<>(List.of(Permission.search)));

        // When
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .body(user)
                .post("/")
                .then()
                .statusCode(201);

        // Then
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .body(user)
                .post("/")
                .then()
                .statusCode(409);
    }

}
