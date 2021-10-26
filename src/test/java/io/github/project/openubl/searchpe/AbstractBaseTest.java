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
package io.github.project.openubl.searchpe;

import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import static io.restassured.RestAssured.given;

public abstract class AbstractBaseTest {

    @ConfigProperty(name = "quarkus.oidc.enabled")
    Boolean oidcEnabled;

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String oidcAuthServerUrl;

    @ConfigProperty(name = "quarkus.oidc.client-id")
    String oidcAuthClientId;

    @ConfigProperty(name = "quarkus.oidc.credentials.secret")
    String oidcAuthSecret;

    public abstract Class<?> getTestClass();

    @BeforeEach
    public void beforeEach() {
        Flyway flyway = flyway();
        flyway.clean();
        flyway.migrate();
    }

    @AfterEach
    public void afterEach() {
        Flyway flyway = flyway();
        flyway.clean();
    }

    private Flyway flyway() {
        Config config = ConfigProvider.getConfig();
        String username = config.getValue("quarkus.datasource.username", String.class);
        String password = config.getValue("quarkus.datasource.password", String.class);
        String jdbUrl = config.getValue("quarkus.datasource.jdbc.url", String.class);

        // Flyway
        final String packageName = getTestClass().getName();
        final String testDefaultLocation = "db/" + packageName.replaceAll("\\.", Matcher.quoteReplacement(File.separator));

        final List<String> locations = new ArrayList<>();
        locations.add(testDefaultLocation);
        locations.add("db" + File.separator + "migration");

        if (packageName.startsWith("Native") && packageName.endsWith("IT")) {
            final String className = getTestClass().getSimpleName();
            final String nativeClassName = className.replaceFirst("Native", "").replace("IT", "Test");
            final String testNativeDefaultLocation = "db/" + getTestClass().getPackage().getName().replaceAll("\\.", Matcher.quoteReplacement(File.separator)) + "/" + nativeClassName;

            locations.add(testNativeDefaultLocation);
        }

        return Flyway.configure()
                .dataSource(jdbUrl, username, password)
                .connectRetries(120)
                .locations(locations.toArray(String[]::new))
                .load();
    }

    protected RequestSpecification givenAuth(String username) {
        if (oidcEnabled) {
            return given().auth().oauth2(getAccessToken(username));
        } else {
            return given().auth().basic(username, username);
        }
    }

    private String getAccessToken(String userName) {
        return given()
                .relaxedHTTPSValidation()
                .auth().preemptive().basic(oidcAuthClientId, oidcAuthSecret)
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "password")
                .formParam("username", userName)
                .formParam("password", userName)
                .when()
                .post(oidcAuthServerUrl + "/protocol/openid-connect/token")
                .then().extract().path("access_token").toString();
    }

}
