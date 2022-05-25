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

import io.github.project.openubl.searchpe.AbstractBaseTest;
import io.github.project.openubl.searchpe.DefaultProfileManager;
import io.github.project.openubl.searchpe.dto.VersionDto;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(DefaultProfileManager.class)
@TestHTTPEndpoint(VersionResource.class)
public class VersionResourceTest extends AbstractBaseTest {

    @Override
    public Class<?> getTestClass() {
        return VersionResourceTest.class;
    }

    @Test
    public void getVersions() {
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
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
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("?active=true")
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
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("?active=false")
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
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/" + 1)
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
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/" + 999)
                .then()
                .statusCode(404);
    }

    @Test
    public void deleteVersion() {
        int versionId = 3;

        // When
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .delete("/" + versionId)
                .then()
                .statusCode(204);

        // Then
        await()
                .atMost(3, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    int statusCode = givenAuth("alice")
                            .header("Content-Type", "application/json")
                            .when()
                            .get("/" + versionId)
                            .then()
                            .extract().statusCode();
                    assertEquals(404, statusCode);
                });

        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("?active=true")
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
        VersionDto version = givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .post("/")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract().body().as(VersionDto.class);

        assertNotNull(version);

        // Then
        await()
                .atMost(3, TimeUnit.MINUTES)
                .until(() -> {
                    VersionDto watchedVersion = givenAuth("alice")
                            .header("Content-Type", "application/json")
                            .when()
                            .get("/" + version.getId())
                            .then()
                            .extract().body().as(VersionDto.class);
                    return watchedVersion.getStatus() == Status.COMPLETED;
                });

        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("?active=true")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(1),
                        "[0].id", is(version.getId().intValue()),
                        "[0].status", is("COMPLETED")
                );
    }

}
