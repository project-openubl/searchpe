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

import io.github.project.openubl.searchpe.ProfileManager;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.resources.config.ElasticsearchServer;
import io.github.project.openubl.searchpe.resources.config.SunatServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(ProfileManager.class)
public class VersionResourceTest {

    @Test
    public void getVersions() {
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(3),
                        "[0].status", is(Status.COMPLETED.toString()),
                        "[1].status", is(Status.COMPLETED.toString()),
                        "[2].status", is(Status.ERROR.toString())
                );
    }

    @Test
    public void getVersions_onlyActive() {
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions?active=true")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(1),
                        "[0].id", is(3),
                        "[0].status", is("COMPLETED"),
                        "[0].records", is(9_000_000)
                );

    }

    @Test
    public void getVersions_onlyInactive() {
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions?active=false")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(2),
                        "[0].id", is(2),
                        "[0].status", is("COMPLETED"),
                        "[1].id", is(1),
                        "[1].status", is("ERROR")
                );

    }

    @Test
    public void getVersion() {
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions/" + 1)
                .then()
                .statusCode(200)
                .body(
                        "id", is(1),
                        "status", is(Status.ERROR.toString()),
                        "records", is(1_000_000)
                );
    }

    @Test
    public void getVersion_nonExists() {
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions/" + 999)
                .then()
                .statusCode(404);
    }

    @Test
    public void deleteVersion() {
        int versionId = 3;

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .delete("/versions/" + versionId)
                .then()
                .statusCode(204);

        // Then
        await()
                .atMost(3, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    int statusCode = given()
                            .header("Content-Type", "application/json")
                            .when()
                            .get("/versions/" + versionId)
                            .then()
                            .extract().statusCode();
                    assertEquals(404, statusCode);
                });

        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions?active=true")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(1),
                        "[0].id", is(2),
                        "[0].status", is("COMPLETED"),
                        "[0].records", is(5_000_000)
                );
    }

    @Test
    public void createVersion() {
        // Given

        // When
        VersionEntity version = given()
                .header("Content-Type", "application/json")
                .when()
                .post("/versions")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract().body().as(VersionEntity.class);

        assertNotNull(version);

        // Then
        await()
                .atMost(3, TimeUnit.MINUTES)
                .until(() -> {
                    VersionEntity watchedVersion = given()
                            .header("Content-Type", "application/json")
                            .when()
                            .get("/versions/" + version.id)
                            .then()
                            .extract().body().as(VersionEntity.class);
                    return watchedVersion.status == Status.COMPLETED;
                });

        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions?active=true")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(1),
                        "[0].id", is(version.id.intValue()),
                        "[0].status", is("COMPLETED")
                );
    }

}
