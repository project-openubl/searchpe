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

import io.github.project.openubl.searchpe.utils.DataHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileServiceOpenPadronTest {

    @TempDir
    Path workspace;

    private final FileService fileService = new FileService();

    @Test
    public void openPadronReadsTheFirstTxtEntry() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("folder/", "");
        entries.put("README.md", "not the padrón");
        entries.put("folder/padron_reducido_ruc.txt", "RUC|NOMBRE\n10452159428|PEÑA\n");
        entries.put("other.txt", "not the padrón either");
        File zipFile = createZip(entries);

        try (BufferedReader padron = fileService.openPadron(zipFile)) {
            assertEquals("RUC|NOMBRE", padron.readLine());
            assertEquals("10452159428|PEÑA", padron.readLine());
        }
    }

    @Test
    public void openPadronFailsWithoutTxtEntry() throws Exception {
        File zipFile = createZip(Map.of("README.md", "not the padrón"));

        assertThrows(IOException.class, () -> fileService.openPadron(zipFile));
    }

    private File createZip(Map<String, String> entries) throws IOException {
        Path zipPath = workspace.resolve("padron.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(DataHelper.PADRON_CHARSET));
                zip.closeEntry();
            }
        }
        return zipPath.toFile();
    }
}
