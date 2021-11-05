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
import io.github.project.openubl.searchpe.models.RoleType;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.*;
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
        BasicUserEntity user = new BasicUserEntity();
        user.username = "newUser";
        user.password = "newPassword";
        user.role = "newRole";

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
                        "[0].role", is("admin"),

                        "[1].id", is(notNullValue()),
                        "[1].username", is("alice"),
                        "[1].password", is(nullValue()),
                        "[1].role", is("user")
                );
    }

    @Test
    public void getOneUser() {
        BasicUserEntity[] users = givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .extract().body().as(BasicUserEntity[].class);

        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/" + users[0].id)
                .then()
                .statusCode(200)
                .body(
                        "id", is(users[0].id.intValue()),
                        "username", is(notNullValue()),
                        "password", is(nullValue()),
                        "role", is(notNullValue())
                );
    }

    @Test
    public void updateUser() {
        // Given
        BasicUserEntity[] users = givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .extract().body().as(BasicUserEntity[].class);

        BasicUserEntity userToUpdate = users[0];
        userToUpdate.username = "newUsername";
        userToUpdate.password = "newPassword";

        // Then
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .body(userToUpdate)
                .put("/" + userToUpdate.id)
                .then()
                .statusCode(204);
    }

    @Test
    public void deleteUser() {
        // Given
        BasicUserEntity[] users = givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .extract().body().as(BasicUserEntity[].class);

        Optional<BasicUserEntity> userToDeleteOptional = Stream.of(users).filter(f -> f.role.equals(RoleType.user.toString())).findFirst();
        assertTrue(userToDeleteOptional.isPresent());
        BasicUserEntity userToDelete = userToDeleteOptional.get();

        // When
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .delete("/" + userToDelete.id)
                .then()
                .statusCode(200);

        // Then
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/" + userToDelete.id)
                .then()
                .statusCode(404);
    }

    @Test
    public void deleteAdminUserNotAllowed() {
        // Given
        BasicUserEntity[] users = givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .extract().body().as(BasicUserEntity[].class);

        Optional<BasicUserEntity> userToDeleteOptional = Stream.of(users).filter(f -> f.role.equals(RoleType.admin.toString())).findFirst();
        assertTrue(userToDeleteOptional.isPresent());
        BasicUserEntity userToDelete = userToDeleteOptional.get();

        // When
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .delete("/" + userToDelete.id)
                .then()
                .statusCode(400);
    }

    @Test
    public void createUser() {
        // Given
        BasicUserEntity user = new BasicUserEntity();
        user.username = "myUsername";
        user.password = "myPassword";
        user.role = RoleType.user.toString();

        // When
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .body(user)
                .post("/")
                .then()
                .statusCode(201);
    }

    @Test
    public void createDuplicateAdminUserNotAllowed() {
        // Given
        BasicUserEntity user = new BasicUserEntity();
        user.username = "myUsername";
        user.password = "myPassword";
        user.role = RoleType.admin.toString();

        // When
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .body(user)
                .post("/")
                .then()
                .statusCode(400);
    }

}
