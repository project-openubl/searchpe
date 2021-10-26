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

import io.quarkus.test.junit.callback.QuarkusTestAfterEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class FlywayTestCallback implements QuarkusTestBeforeEachCallback, QuarkusTestAfterEachCallback {

    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        Flyway flyway = flyway(context);
        flyway.clean();
        flyway.migrate();
    }

    @Override
    public void afterEach(QuarkusTestMethodContext context) {
        Flyway flyway = flyway(context);
        flyway.clean();
    }

    private Flyway flyway(final QuarkusTestMethodContext context) {
        Config config = ConfigProvider.getConfig();
        String username = config.getValue("quarkus.datasource.username", String.class);
        String password = config.getValue("quarkus.datasource.password", String.class);
        String jdbUrl = config.getValue("quarkus.datasource.jdbc.url", String.class);

        // Flyway
        final String packageName = context.getTestInstance().getClass().getName();
        final String testDefaultLocation = "db/" + packageName.replaceAll("\\.", Matcher.quoteReplacement(File.separator));

        final List<String> locations = new ArrayList<>();
        locations.add(testDefaultLocation);
        locations.add("db" + File.separator + "migration");

        if (packageName.startsWith("Native") && packageName.endsWith("IT")) {
            final String className = context.getTestInstance().getClass().getSimpleName();
            final String nativeClassName = className.replaceFirst("Native", "").replace("IT", "Test");
            final String testNativeDefaultLocation = "db/" + context.getTestInstance().getClass().getPackage().getName().replaceAll("\\.", Matcher.quoteReplacement(File.separator)) + "/" + nativeClassName;

            locations.add(testNativeDefaultLocation);
        }

        return Flyway.configure()
                .dataSource(jdbUrl, username, password)
                .connectRetries(120)
                .locations(locations.toArray(String[]::new))
                .load();
    }

}
