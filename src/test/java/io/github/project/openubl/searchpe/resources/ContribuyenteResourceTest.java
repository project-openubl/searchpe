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
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestProfile(BasicProfileManager.class)
@TestHTTPEndpoint(ContribuyenteResource.class)
public class ContribuyenteResourceTest extends AbstractBaseTest {

    @Override
    public Class<?> getTestClass() {
        return ContribuyenteResourceTest.class;
    }

    @Test
    public void getContribuyente() {
        // Given
        String numeroDocumento = "11111111111";

        // When
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/" + numeroDocumento)
                .then()
                .statusCode(200)
                .body(
                        "numeroDocumento", is(numeroDocumento),
                        "nombre", is("mi empresa1")
                );

    }

    @Test
    public void getContribuyente_notFound() {
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/someNumeroDocumento")
                .then()
                .statusCode(404);
    }

}
