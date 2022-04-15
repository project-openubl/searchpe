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
package io.github.project.openubl.searchpe;

import io.github.project.openubl.searchpe.resources.config.SunatServer;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.ArrayList;
import java.util.List;

public abstract class ProfileManager implements QuarkusTestProfile {

    enum DistributionFlavor {
        standalone, enterprise
    }

    protected String configProfile;
    protected List<TestResourceEntry> testResources = new ArrayList<>();

    public ProfileManager(DistributionFlavor distributionFlavor) {
        init(distributionFlavor);
    }

    private void init(DistributionFlavor distributionFlavor) {
        switch (distributionFlavor) {
            case standalone:
                // Profile
                configProfile = DistributionFlavor.standalone.toString();

                // Test resources
                testResources.add(new TestResourceEntry(SunatServer.class));

                break;
            case enterprise:
                // Profile
                configProfile = DistributionFlavor.enterprise.toString();

                // Test resources
                testResources.add(new TestResourceEntry(SunatServer.class));

                break;
        }
    }

    @Override
    public String getConfigProfile() {
        return "test," + configProfile;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return testResources;
    }
}
