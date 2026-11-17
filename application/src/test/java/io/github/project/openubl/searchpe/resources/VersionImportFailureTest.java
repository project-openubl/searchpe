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
import io.github.project.openubl.searchpe.UnreachableSunatProfileManager;
import io.github.project.openubl.searchpe.dto.VersionDto;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@QuarkusTest
@TestProfile(UnreachableSunatProfileManager.class)
@TestHTTPEndpoint(VersionResource.class)
public class VersionImportFailureTest extends AbstractBaseTest {

    @Override
    public Class<?> getTestClass() {
        return VersionImportFailureTest.class;
    }

    @Test
    public void createVersionMarksTheVersionAsErrorWhenTheImportFails() {
        // Given
        VersionDto version = givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .post("/")
                .then()
                .statusCode(200)
                .extract().body().as(VersionDto.class);

        // Then
        await().atMost(1, TimeUnit.MINUTES)
                .until(() -> givenAuth("alice")
                        .header("Content-Type", "application/json")
                        .when()
                        .get("/" + version.getId())
                        .then()
                        .extract().body().as(VersionDto.class)
                        .getStatus() == Status.ERROR
                );
    }
}
