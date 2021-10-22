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

import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.resources.config.ElasticsearchServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.NativeImageTest;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@Disabled
@NativeImageTest
@QuarkusTestResource(ElasticsearchServer.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NativeResourceIT {

    @Order(1)
    @Test
    public void verifyImportData() {
        // Given

        // When
        VersionEntity versionCreated = given()
                .header("Content-Type", "application/json")
                .when()
                .post("/versions")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract()
                .as(VersionEntity.class);

        // Then
        await()
                .atMost(10, TimeUnit.MINUTES)
                .until(() -> {
                    VersionEntity versionWatched = given()
                            .header("Content-Type", "application/json")
                            .when()
                            .get("/versions/" + versionCreated.id)
                            .then()
                            .extract()
                            .as(VersionEntity.class);

                    return versionWatched.status == Status.COMPLETED;
                });
    }

    @Order(2)
    @Test
    public void verifyContribuyentes() {
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/contribuyentes")
                .then()
                .statusCode(200)
                .body(
                        "meta.offset", is(0),
                        "meta.limit", is(10),
                        "meta.count", is(422),
                        "data.size()", is(10)
                );
    }

}

