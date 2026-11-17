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
package io.github.project.openubl.searchpe.models.jpa;

import io.github.project.openubl.searchpe.AbstractBaseTest;
import io.github.project.openubl.searchpe.DefaultProfileManager;
import io.github.project.openubl.searchpe.services.VersionService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(DefaultProfileManager.class)
public class VersionRepositoryTest extends AbstractBaseTest {

    @Inject
    VersionRepository versionRepository;

    @Override
    public Class<?> getTestClass() {
        return VersionRepositoryTest.class;
    }

    @Test
    public void listObsoleteReturnsFinishedAndStaleVersionsExceptTheActiveOne() {
        Date staleBefore = Date.from(Instant.now().minus(VersionService.IMPORT_TIMEOUT));

        List<Long> obsoleteIds = versionRepository.listObsolete(staleBefore).stream()
                .map(version -> version.id)
                .sorted()
                .toList();

        assertEquals(List.of(1L, 3L, 4L, 7L, 8L), obsoleteIds);
    }
}
