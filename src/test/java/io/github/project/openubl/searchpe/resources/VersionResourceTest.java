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
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteId;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

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
                .get("/versions")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(2),
                        "[0].status", is(Status.COMPLETED.toString()),
                        "[1].status", is(Status.ERROR.toString())
                );
    }

    @Test
    public void getVersions_onlyActive() {
        // Given
        Calendar calendar = Calendar.getInstance();
        VersionEntity version1 = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.COMPLETED)
                .withCreatedAt(calendar.getTime())
                .withUpdatedAt(calendar.getTime())
                .build();

        calendar.add(Calendar.SECOND, 10);
        VersionEntity version2 = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.COMPLETED)
                .withCreatedAt(calendar.getTime())
                .withUpdatedAt(calendar.getTime())
                .build();

        versionRepository.persist(version1, version2);

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions?active=true")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(1),
                        "[0].id", is(version2.id.intValue())
                );

    }

    @Test
    public void getVersions_onlyInactive() {
        // Given
        Calendar calendar = Calendar.getInstance();

        VersionEntity version1 = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.ERROR)
                .withCreatedAt(calendar.getTime())
                .withUpdatedAt(calendar.getTime())
                .build();
        VersionEntity version2 = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.COMPLETED)
                .withCreatedAt(calendar.getTime())
                .withUpdatedAt(calendar.getTime())
                .build();

        calendar.add(Calendar.SECOND, 10);
        VersionEntity version3 = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.COMPLETED)
                .withCreatedAt(calendar.getTime())
                .withUpdatedAt(calendar.getTime())
                .build();

        versionRepository.persist(version1, version2, version3);

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions?active=false")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(2),
                        "[0].id", is(version2.id.intValue()),
                        "[1].id", is(version1.id.intValue())
                );

    }

    @Test
    public void createVersion() {
        // Given

        // When
        ExtractableResponse<Response> newVersionResponse = given()
                .header("Content-Type", "application/json")
                .when()
                .post("/versions")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract();
        VersionEntity version = newVersionResponse.as(VersionEntity.class);
        assertNotNull(version);

        // Then
        await()
                .atMost(3, TimeUnit.MINUTES)
                .until(() -> {
                    VersionEntity versionEntity = versionRepository.findById(version.id);
                    List<ContribuyenteEntity> contribuyentes = contribuyenteRepository.listAll();
                    return versionEntity.status == Status.COMPLETED && contribuyentes.size() > 1;
                });

        VersionEntity activeVersion = versionRepository.findActive().orElse(null);
        assertEquals(version, activeVersion);
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
                .get("/versions/" + version.id)
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
                .get("/versions/1")
                .then()
                .statusCode(404);
    }

    @Test
    public void deleteVersion() {
        // Given
        VersionEntity version1 = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.COMPLETED)
                .withCreatedAt(new Date())
                .withUpdatedAt(new Date())
                .build();
        VersionEntity version2 = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.COMPLETED)
                .withCreatedAt(new Date())
                .withUpdatedAt(new Date())
                .build();
        versionRepository.persist(version1, version2);

        ContribuyenteEntity contribuyente1 = ContribuyenteEntity.Builder.aContribuyenteEntity()
                .withId(new ContribuyenteId(version2.id, "11111111111"))
                .withRazonSocial("company1")
                .build();
        ContribuyenteEntity contribuyente2 = ContribuyenteEntity.Builder.aContribuyenteEntity()
                .withId(new ContribuyenteId(version2.id, "22222222222"))
                .withRazonSocial("company2")
                .build();
        contribuyenteRepository.persist(contribuyente1, contribuyente2);

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .delete("/versions/" + version2.id)
                .then()
                .statusCode(204);

        // Then
        await()
                .atMost(3, TimeUnit.MINUTES)
                .until(() -> {
                    VersionEntity versionEntity = versionRepository.findById(version2.id);
                    return versionEntity == null;
                });

        assertTrue(versionRepository.findByIdOptional(version2.id).isEmpty());
    }
}
