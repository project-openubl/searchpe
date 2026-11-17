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
package io.github.project.openubl.searchpe.services;

import io.github.project.openubl.searchpe.AbstractBaseTest;
import io.github.project.openubl.searchpe.DefaultProfileManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(DefaultProfileManager.class)
public class LegacyIndexCleanupTest extends AbstractBaseTest {

    @Inject
    VersionService versionService;

    @Override
    public Class<?> getTestClass() {
        return LegacyIndexCleanupTest.class;
    }

    @Test
    public void deleteVersionDropsTheLegacyParentIndex() {
        assertEquals(List.of("ix_contribuyente_ruc_version_1"), indexNamesOf("contribuyente"));

        versionService.deleteVersion(1L);

        assertEquals(List.of(), indexNamesOf("contribuyente"));
    }
}
