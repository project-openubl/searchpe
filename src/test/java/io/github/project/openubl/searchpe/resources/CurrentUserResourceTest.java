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
import io.quarkus.test.common.http.TestHTTPEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestHTTPEndpoint(CurrentUserResource.class)
public abstract class CurrentUserResourceTest extends AbstractBaseTest {

    @Override
    public Class<?> getTestClass() {
        return CurrentUserResourceTest.class;
    }

    @Test
    public void whoAmITest() {
        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .get("/whoami")
                .then()
                .statusCode(200)
                .body(
                        "username", is("alice"),
                        "role", is("user")
                );

        givenAuth("admin")
                .header("Content-Type", "application/json")
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .body(
                        "username", is("admin"),
                        "role", is("admin")
                );
    }

    @Test
    public void whoAmI_userAdmin() {

    }

}
