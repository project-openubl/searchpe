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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(PostgreSQLServer.class)
public class ContribuyenteResourceTest {

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
    public void getContribuyentes() {
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

        ContribuyenteEntity contribuyente1 = ContribuyenteEntity.Builder.aContribuyenteEntity()
                .withId(UUID.randomUUID().toString())
                .withRuc("11111111111")
                .withRazonSocial("razonSocial1")
                .withVersion(version2)
                .build();
        ContribuyenteEntity contribuyente2 = ContribuyenteEntity.Builder.aContribuyenteEntity()
                .withId(UUID.randomUUID().toString())
                .withRuc("22222222222")
                .withRazonSocial("razonSocial2")
                .withVersion(version2)
                .build();
        ContribuyenteEntity contribuyente3 = ContribuyenteEntity.Builder.aContribuyenteEntity()
                .withId(UUID.randomUUID().toString())
                .withRuc("33333333333")
                .withRazonSocial("razonSocial3")
                .withVersion(version2)
                .build();
        contribuyenteRepository.persist(contribuyente1, contribuyente2, contribuyente3);

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/api/contribuyentes")
                .then()
                .statusCode(200)
                .body(
                        "meta.offset", is(0),
                        "meta.limit", is(10),
                        "meta.count", is(3),
                        "data.size()", is(3),
                        "data[0].ruc", is(contribuyente1.ruc),
                        "data[1].ruc", is(contribuyente2.ruc),
                        "data[2].ruc", is(contribuyente3.ruc)
                );

    }

    @Test
    public void getContribuyente() {
        // Given
        VersionEntity version = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.COMPLETED)
                .withCreatedAt(new Date())
                .withUpdatedAt(new Date())
                .build();
        versionRepository.persist(version);

        ContribuyenteEntity contribuyente = ContribuyenteEntity.Builder.aContribuyenteEntity()
                .withId(UUID.randomUUID().toString())
                .withRuc("11111111111")
                .withRazonSocial("razonSocial1")
                .withVersion(version)
                .build();
        contribuyenteRepository.persist(contribuyente);

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/api/contribuyentes/" + contribuyente.ruc)
                .then()
                .statusCode(200)
                .body(
                        "ruc", is(contribuyente.ruc),
                        "razonSocial", is(contribuyente.razonSocial)
                );

    }

    @Test
    public void getContribuyente_notFound() {
        // Given
        VersionEntity version = VersionEntity.Builder.aVersionEntity()
                .withStatus(Status.COMPLETED)
                .withCreatedAt(new Date())
                .withUpdatedAt(new Date())
                .build();
        versionRepository.persist(version);

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/api/contribuyentes/someRuc")
                .then()
                .statusCode(404);
    }

}
