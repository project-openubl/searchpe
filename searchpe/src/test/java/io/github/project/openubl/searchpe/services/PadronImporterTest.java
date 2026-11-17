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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(DefaultProfileManager.class)
public class PadronImporterTest extends AbstractBaseTest {

    @Inject
    PadronImporter padronImporter;

    @TempDir
    Path workspace;

    private final ExecutorService executorService = Executors.newFixedThreadPool(PadronImporter.REQUIRED_THREADS);

    @Override
    public Class<?> getTestClass() {
        return PadronImporterTest.class;
    }

    @AfterEach
    public void shutdownExecutor() {
        executorService.shutdownNow();
    }

    @Test
    public void importPadronFailsWhenAChunkCanNotBeCopied() throws Exception {
        Path zipFile = workspace.resolve("padron_reducido_ruc.zip");
        try (InputStream fixture = getClass().getClassLoader().getResourceAsStream("padron_reducido_ruc.zip")) {
            Files.copy(fixture, zipFile);
        }

        // Version 999 has no partition, so every COPY fails
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> padronImporter.importPadron(999L, zipFile.toFile(), executorService)
        );
        assertTrue(exception.getMessage().contains("Version 999"), exception.getMessage());
    }
}
