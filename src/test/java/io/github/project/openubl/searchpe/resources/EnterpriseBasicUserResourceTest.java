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
import io.github.project.openubl.searchpe.EnterpriseProfileManager;
import io.github.project.openubl.searchpe.models.RoleType;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(EnterpriseProfileManager.class)
@TestHTTPEndpoint(BasicUserResource.class)
public class EnterpriseBasicUserResourceTest extends AbstractBaseTest {

    @Override
    public Class<?> getTestClass() {
        return EnterpriseBasicUserResourceTest.class;
    }

    @Test
    public void getDefaultUsers() {
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(400);
    }

    @Test
    public void getOneUser() {
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/1")
                .then()
                .statusCode(400);
    }

    @Test
    public void updateUser() {
        // Given
        BasicUserEntity userToUpdate = new BasicUserEntity();
        userToUpdate.username = "newUsername";
        userToUpdate.password = "newPassword";

        // Then
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .body(userToUpdate)
                .put("/1")
                .then()
                .statusCode(400);
    }

    @Test
    public void deleteUser() {
        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/1")
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
                .statusCode(400);
    }

}
