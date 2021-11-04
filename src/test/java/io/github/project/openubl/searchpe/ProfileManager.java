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

import io.github.project.openubl.searchpe.resources.config.ElasticsearchServer;
import io.github.project.openubl.searchpe.resources.config.KeycloakServer;
import io.github.project.openubl.searchpe.resources.config.PostgresSQLServer;
import io.github.project.openubl.searchpe.resources.config.SunatServer;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileManager implements QuarkusTestProfile {

    static final String testModeKey = "searchpe.test.mode";

    enum DistributionFlavor {
        standalone, enterprise
    }

    String configProfile;
    List<TestResourceEntry> testResources = new ArrayList<>();
    Map<String, String> configOverrides = new HashMap<>();

    public ProfileManager() {
        String testModeFlavor = System.getProperty(testModeKey, DistributionFlavor.standalone.toString());
        DistributionFlavor distributionFlavor = DistributionFlavor.valueOf(testModeFlavor);

        init(distributionFlavor);
    }

    public ProfileManager(DistributionFlavor distributionFlavor) {
        init(distributionFlavor);
    }

    private void init(DistributionFlavor distributionFlavor) {
        configOverrides.put("quarkus.datasource.devservices.enabled", "false");
        configOverrides.put("quarkus.keycloak.devservices.enabled", "false");

        switch (distributionFlavor) {
            case standalone:
                // Profile
                configProfile = "test";

                // Test resources
                testResources.add(new TestResourceEntry(PostgresSQLServer.class));
                testResources.add(new TestResourceEntry(SunatServer.class));

                // Config
                configOverrides.put("quarkus.hibernate-search-orm.automatic-indexing.synchronization.strategy", "bean:searchpeNoneIndexer");

                configOverrides.put("quarkus.oidc.auth-server-url", "http://localhost:8180/auth"); // Required to have this prop for running tests
                configOverrides.put("quarkus.oidc.client-id", "searchpe"); // Required to have this prop for running tests
                configOverrides.put("quarkus.oidc.credentials.secret", "secret"); // Required to have this prop for running tests

                break;
            case enterprise:
                // Profile
                configProfile = DistributionFlavor.enterprise.toString();

                // Test resources
                testResources.add(new TestResourceEntry(PostgresSQLServer.class));
                testResources.add(new TestResourceEntry(SunatServer.class));
                testResources.add(new TestResourceEntry(ElasticsearchServer.class));
                testResources.add(new TestResourceEntry(KeycloakServer.class));

                // Config
                configOverrides.put("quarkus.hibernate-search-orm.automatic-indexing.synchronization.strategy", "sync");
                configOverrides.put("quarkus.oidc.enabled", "true"); // Without this ti doesn't take effect and tests fail

                break;
        }
    }

    @Override
    public String getConfigProfile() {
        return configProfile;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return testResources;
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return configOverrides;
    }
}
