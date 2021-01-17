/**
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

import io.github.project.openubl.searchpe.models.jpa.ContribuyenteRepository;
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.resources.config.PostgreSQLServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(PostgreSQLServer.class)
public class VersionResourceTest {

    @Inject
    VersionRepository versionRepository;

    @Inject
    ContribuyenteRepository contribuyenteRepository;

    @AfterEach
    public void afterEach() {
        contribuyenteRepository.deleteAll();
        versionRepository.deleteAll();
    }

    @Test
    public void getVersions() {
        // Given
        VersionEntity version1 = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.ERROR)
                .withCreatedAt(new Date())
                .withUpdatedAt(new Date())
                .build();
        VersionEntity version2 = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.COMPLETED)
                .withCreatedAt(new Date())
                .withUpdatedAt(new Date())
                .build();

        versionRepository.persist(version1, version2);

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/api/versions")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(2),
                        "[0].status", is(Status.COMPLETED.toString()),
                        "[1].status", is(Status.ERROR.toString())
                );

    }

    @Test
    public void getVersion() {
        // Given
        VersionEntity version = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.ERROR)
                .withCreatedAt(new Date())
                .withUpdatedAt(new Date())
                .build();

        versionRepository.persist(version);

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/api/versions/" + version.id)
                .then()
                .statusCode(200)
                .body(
                        "status", is(Status.ERROR.toString())
                );

    }

    @Test
    public void getVersion_nonExists() {
        // Given

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/api/versions/1")
                .then()
                .statusCode(404);

    }

    @Test
    public void createVersion() {
        // Given

        // When
        ExtractableResponse<Response> newVersionResponse = given()
                .header("Content-Type", "application/json")
                .when()
                .post("/api/versions")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract();
        VersionEntity version = newVersionResponse.as(VersionEntity.class);

        // Then
        await()
                .atMost(10, TimeUnit.MINUTES)
                .until(() -> {
                    VersionEntity versionEntity = versionRepository.findById(version.id);
                    List<ContribuyenteEntity> contribuyentes = contribuyenteRepository.listAll();
                    return versionEntity.status == Status.COMPLETED && contribuyentes.size() > 1;
                });
    }
}
