/**
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 * <p>
 * Licensed under the Eclipse Public License - v 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.eclipse.org/legal/epl-2.0/
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.searchpe.resources.config;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.Map;

public class PostgreSQLServer implements QuarkusTestResourceLifecycleManager {

    private GenericContainer postgreSQL;

    @Override
    public Map<String, String> start() {
        postgreSQL = new FixedHostPortGenericContainer("postgres:" + System.getProperty("postgresql.version", "13.1"))
                .withFixedExposedPort(5432, 5432)
                .withEnv("POSTGRES_USER", "searchpe_username")
                .withEnv("POSTGRES_PASSWORD", "searchpe_password")
                .withEnv("POSTGRES_DB", "searchpe_db")
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\\\s", 5));
        postgreSQL.start();
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        postgreSQL.stop();
    }
}
