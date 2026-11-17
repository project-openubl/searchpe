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
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestProfile(DefaultProfileManager.class)
@TestHTTPEndpoint(VersionResource.class)
public class VersionCancellationTest extends AbstractBaseTest {

    @Override
    public Class<?> getTestClass() {
        return VersionCancellationTest.class;
    }

    /**
     * The import may run on another replica, so the outcome depends on the status only: a running import is asked to
     * stop (CANCELLING), anything else is cancelled right away.
     */
    @ParameterizedTest
    @CsvSource({
            "1, CANCELLING",
            "4, CANCELLING",
            // Asking again must not mark it CANCELLED while the import still runs
            "3, CANCELLING",
            "2, CANCELLED",
    })
    public void cancelVersion(long versionId, Status expectedStatus) {
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .body("{\"status\": \"CANCELLING\"}")
                .when()
                .put("/" + versionId)
                .then()
                .statusCode(204);

        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/" + versionId)
                .then()
                .statusCode(200)
                .body("status", is(expectedStatus.toString()));
    }
}
