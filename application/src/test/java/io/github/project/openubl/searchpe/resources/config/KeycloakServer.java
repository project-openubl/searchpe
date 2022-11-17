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
package io.github.project.openubl.searchpe.resources.config;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.HashMap;
import java.util.Map;

public class KeycloakServer implements QuarkusTestResourceLifecycleManager {

    public static final int CONTAINER_PORT = 8080;
    public static final String TAG = "15.0.2";

    private GenericContainer<?> keycloak;

    @Override
    public Map<String, String> start() {
        keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:" + TAG)
                .withExposedPorts(CONTAINER_PORT)
                .withEnv("DB_VENDOR", "H2")
                .withEnv("KEYCLOAK_USER", "admin")
                .withEnv("KEYCLOAK_PASSWORD", "admin")
                .withEnv("KEYCLOAK_IMPORT", "/tmp/realm.json")
                .withClasspathResourceMapping("openubl-realm.json", "/tmp/realm.json", BindMode.READ_ONLY)
                .waitingFor(Wait.forHttp("/auth"));
        keycloak.start();

        String host = keycloak.getHost();
        Integer port = keycloak.getMappedPort(CONTAINER_PORT);

        return new HashMap<>() {{
            put("quarkus.oidc.client-id", "searchpe");
            put("quarkus.oidc.credentials.secret", "secret");
            put("quarkus.oidc.auth-server-url", "http://" + host + ":" + port + "/auth/realms/openubl");
        }};
    }

    @Override
    public void stop() {
        keycloak.stop();
    }
}
