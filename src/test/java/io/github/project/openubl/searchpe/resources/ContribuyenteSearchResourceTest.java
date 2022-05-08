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
import io.github.project.openubl.searchpe.BasicProfileManager;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(BasicProfileManager.class)
public class ContribuyenteSearchResourceTest extends AbstractBaseTest {

    @Override
    public Class<?> getTestClass() {
        return ContribuyenteSearchResourceTest.class;
    }

    @Test
    public void getContribuyentes() {
        // Given
        VersionEntity version = givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .post("/api/versions")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract().body().as(VersionEntity.class);

        assertNotNull(version);

        await().atMost(3, TimeUnit.MINUTES)
                .until(() -> {
                    VersionEntity watchedVersion = givenAuth("alice")
                            .header("Content-Type", "application/json")
                            .when()
                            .get("/api/versions/" + version.id)
                            .then()
                            .extract().body().as(VersionEntity.class);
                    return watchedVersion.status == Status.COMPLETED;
                });

        // Then
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/api/contribuyentes")
                .then()
                .statusCode(200)
                .body(
                        "meta.offset", is(0),
                        "meta.limit", is(10),
                        "meta.count", is(424),
                        "data.size()", is(10)
                );
    }

    @Test
    public void getContribuyentesUsingTextFilter() {
        // Given
        VersionEntity version = givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .post("/api/versions")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract().body().as(VersionEntity.class);

        assertNotNull(version);

        await().atMost(3, TimeUnit.MINUTES)
                .until(() -> {
                    VersionEntity watchedVersion = givenAuth("alice")
                            .header("Content-Type", "application/json")
                            .when()
                            .get("/api/versions/" + version.id)
                            .then()
                            .extract().body().as(VersionEntity.class);
                    return watchedVersion.status == Status.COMPLETED;
                });

        // Then
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/api/contribuyentes?filterText=carlos")
                .then()
                .statusCode(200)
                .body(
                        "meta.offset", is(0),
                        "meta.limit", is(10),
                        "meta.count", is(1),
                        "data.size()", is(1),
                        "data[0].ruc", is("10452159428"),
                        "data[0].dni", is("45215942"),
                        "data[0].nombre", is("GARCIA CHANCO CARLOS AUGUSTO")
                );
    }

    @Test
    public void getContribuyentesUsingTipoPersonaFilter() {
        // Given
        VersionEntity version = givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .post("/api/versions")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract().body().as(VersionEntity.class);

        assertNotNull(version);

        await().atMost(3, TimeUnit.MINUTES)
                .until(() -> {
                    VersionEntity watchedVersion = givenAuth("alice")
                            .header("Content-Type", "application/json")
                            .when()
                            .get("/api/versions/" + version.id)
                            .then()
                            .extract().body().as(VersionEntity.class);
                    return watchedVersion.status == Status.COMPLETED;
                });

        // Then
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/api/contribuyentes?tipoPersona=natural")
                .then()
                .statusCode(200)
                .body(
                        "meta.offset", is(0),
                        "meta.limit", is(10),
                        "meta.count", is(20),
                        "data.size()", is(10),
                        "data[0].dni", matchesPattern("^[0-9]{8}$")
                );
    }

    @Test
    public void getContribuyentesUsingFilterTextAndTipoPersonaFilter() {
        // Given
        VersionEntity version = givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .post("/api/versions")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract().body().as(VersionEntity.class);

        assertNotNull(version);

        await().atMost(3, TimeUnit.MINUTES)
                .until(() -> {
                    VersionEntity watchedVersion = givenAuth("alice")
                            .header("Content-Type", "application/json")
                            .when()
                            .get("/api/versions/" + version.id)
                            .then()
                            .extract().body().as(VersionEntity.class);
                    return watchedVersion.status == Status.COMPLETED;
                });

        // Then
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/api/contribuyentes?filterText=carlos&tipoPersona=natural")
                .then()
                .statusCode(200)
                .body(
                        "meta.offset", is(0),
                        "meta.limit", is(10),
                        "meta.count", is(1),
                        "data.size()", is(1),
                        "data[0].dni", is("45215942"),
                        "data[0].nombre", is("GARCIA CHANCO CARLOS AUGUSTO")
                );
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/api/contribuyentes?filterText=carlos&tipoPersona=juridica")
                .then()
                .statusCode(200)
                .body(
                        "meta.offset", is(0),
                        "meta.limit", is(10),
                        "meta.count", is(1),
                        "data.size()", is(1),
                        "data[0].ruc", is("10452159428"),
                        "data[0].nombre", is("GARCIA CHANCO CARLOS AUGUSTO")
                );
    }
}
