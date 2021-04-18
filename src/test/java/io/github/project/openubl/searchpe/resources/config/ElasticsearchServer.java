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
package io.github.project.openubl.searchpe.resources.config;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

public class ElasticsearchServer implements QuarkusTestResourceLifecycleManager {

    private GenericContainer elasticsearch;

    @Override
    public Map<String, String> start() {
        elasticsearch = new FixedHostPortGenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:" + System.getProperty("elasticsearch.version", "7.10.2"))
                .withFixedExposedPort(9200, 9200)
                .withFixedExposedPort(9300, 9300)
                .withEnv("discovery.type", "single-node")
                .waitingFor(
                        new HttpWaitStrategy()
                                .forPort(9200)
                                .forStatusCodeMatching(response -> response == HTTP_OK || response == HTTP_UNAUTHORIZED)
                                .withStartupTimeout(Duration.ofMinutes(2))
                );

        elasticsearch.start();
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        elasticsearch.stop();
    }
}
