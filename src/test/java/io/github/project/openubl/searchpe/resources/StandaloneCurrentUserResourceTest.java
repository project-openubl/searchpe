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

import io.github.project.openubl.searchpe.StandaloneProfileManager;
import io.github.project.openubl.searchpe.idm.BasicUserPasswordChangeRepresentation;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestProfile(StandaloneProfileManager.class)
public class StandaloneCurrentUserResourceTest extends AbstractCurrentUserResourceTest {

    @Test
    public void updateCredentials() {
        BasicUserPasswordChangeRepresentation rep = new BasicUserPasswordChangeRepresentation();
        rep.setNewPassword("newPassword");

        givenAuth("alice")
                .header("Content-Type", "application/json")
                .when()
                .body(rep)
                .post("/credentials")
                .then()
                .statusCode(200);
    }

}
