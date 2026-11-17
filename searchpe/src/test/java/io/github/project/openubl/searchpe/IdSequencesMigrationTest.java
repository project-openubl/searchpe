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

import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(DefaultProfileManager.class)
public class IdSequencesMigrationTest extends AbstractBaseTest {

    /**
     * Highest id inserted by V4.1.9__versions_before_4_2_0.sql.
     */
    private static final long HIGHEST_EXISTING_VERSION_ID = 150;

    @Override
    public Class<?> getTestClass() {
        return IdSequencesMigrationTest.class;
    }

    @Test
    public void newVersionsDontReuseExistingIds() {
        Long newVersionId = QuarkusTransaction.requiringNew().call(() -> {
            VersionEntity version = VersionEntity.generateNew();
            version.persist();
            return version.id;
        });

        assertTrue(
                newVersionId > HIGHEST_EXISTING_VERSION_ID,
                "New version got id " + newVersionId + ", which can collide with existing ones"
        );
    }
}
