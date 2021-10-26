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
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(EnterpriseProfileManager.class)
public class SearchEnterpriseContribuyenteResourceTest extends AbstractBaseTest {

    @Override
    public Class<?> getTestClass() {
        return SearchEnterpriseContribuyenteResourceTest.class;
    }

    @Test
    public void getContribuyentes() {
        // Given
        VersionEntity version = givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .post("/versions")
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
                            .get("/versions/" + version.id)
                            .then()
                            .extract().body().as(VersionEntity.class);
                    return watchedVersion.status == Status.COMPLETED;
                });

        // Then
        givenAuth("alice")
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
