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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

public class ElasticsearchServer implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> elasticsearch;

    @Override
    public Map<String, String> start() {
        elasticsearch = new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:" + System.getProperty("elasticsearch.version", "7.10.2"))
                .withExposedPorts(9200, 9300)
                .withEnv("discovery.type", "single-node")
                .waitingFor(
                        new HttpWaitStrategy()
                                .forPort(9200)
                                .forStatusCodeMatching(response -> response == HTTP_OK || response == HTTP_UNAUTHORIZED)
                                .withStartupTimeout(Duration.ofMinutes(2))
                );

        elasticsearch.start();

        String host = elasticsearch.getHost();
        Integer port = elasticsearch.getMappedPort(9200);

        return new HashMap<>() {{
            put("quarkus.hibernate-search-orm.elasticsearch.hosts", host + ":" + port);
            put("quarkus.hibernate-search-orm.elasticsearch.version", "7");
        }};
    }

    @Override
    public void stop() {
        elasticsearch.stop();
    }
}
