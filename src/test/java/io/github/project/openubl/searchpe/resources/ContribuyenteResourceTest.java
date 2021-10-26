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

import io.github.project.openubl.searchpe.AbstractFlywayTest;
import io.github.project.openubl.searchpe.ProfileManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestProfile(ProfileManager.class)
public class ContribuyenteResourceTest extends AbstractFlywayTest {

    @Override
    public Class<?> getTestClass() {
        return ContribuyenteResourceTest.class;
    }

    @Test
    public void getContribuyente() {
        // Given
        String ruc = "11111111111";

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/contribuyentes/" + ruc)
                .then()
                .statusCode(200)
                .body(
                        "ruc", is(ruc),
                        "razonSocial", is("mi empresa1")
                );

    }

    @Test
    public void getContribuyente_notFound() {
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/contribuyentes/someRuc")
                .then()
                .statusCode(404);
    }

}
