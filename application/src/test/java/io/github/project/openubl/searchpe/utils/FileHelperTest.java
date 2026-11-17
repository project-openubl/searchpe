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
package io.github.project.openubl.searchpe.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileHelperTest {

    public static final Path FOLDER_TEST = Paths.get("fileUtilsTestFolder");
    public static final Path DEFAULT_FILE_PATH = FOLDER_TEST.resolve("file.txt");
    private static final byte[] ZIP_ENTRY_CONTENT = {1, 2, 3};

    private void createFileWithRandomContent(Path path) throws Exception {
        org.apache.commons.io.FileUtils.writeByteArrayToFile(path.toFile(), new byte[]{1, 2, 3});
    }

    @BeforeAll
    public static void before() throws IOException {
        org.apache.commons.io.FileUtils.deleteDirectory(FOLDER_TEST.toFile());
    }

    @AfterAll
    public static void after() throws IOException {
        org.apache.commons.io.FileUtils.deleteDirectory(FOLDER_TEST.toFile());
    }

    @Test
    public void test_shouldDeleteFilesIfExists() throws Exception {
        createFileWithRandomContent(DEFAULT_FILE_PATH);
        assertTrue(Files.exists(DEFAULT_FILE_PATH));

        FileHelper.deleteFilesIfExists(new String[]{DEFAULT_FILE_PATH.toAbsolutePath().toString()});
        assertFalse(Files.exists(DEFAULT_FILE_PATH));
    }

    @Test
    public void test_shouldDoNotThrownExceptionIfFileDoesNotExists() throws Exception {
        assertFalse(Files.exists(Paths.get(DEFAULT_FILE_PATH.toAbsolutePath().toString())));
        FileHelper.deleteFilesIfExists(new String[]{DEFAULT_FILE_PATH.toAbsolutePath().toString()});
    }

    @Test
    public void test_shouldDeleteDirectoryRecursively() throws Exception {
        Files.createDirectories(FOLDER_TEST.resolve("subFolder1").resolve("subFolder2"));
        createFileWithRandomContent(FOLDER_TEST.resolve(DEFAULT_FILE_PATH));
        assertTrue(Files.exists(FOLDER_TEST.resolve(DEFAULT_FILE_PATH)));

        FileHelper.deleteFilesIfExists(new String[]{FOLDER_TEST.toAbsolutePath().toString()});
        assertFalse(Files.exists(FOLDER_TEST));
    }

    @Test
    public void test_shouldUnzipFile(@TempDir Path tempDir) throws Exception {
        File zipFile = createZip(tempDir.resolve("padron.zip"), "folder/padron_reducido_ruc.txt");
        Path unzipPath = tempDir.resolve("unzipFolder");

        FileHelper.unzipFile(zipFile, unzipPath);

        Path unzippedFile = unzipPath.resolve("folder").resolve("padron_reducido_ruc.txt");
        assertArrayEquals(ZIP_ENTRY_CONTENT, Files.readAllBytes(unzippedFile));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../evil.txt", "folder/../../evil.txt", "/tmp/evil.txt", "."})
    public void test_shouldRejectZipEntriesOutsideTheDestination(String entryName, @TempDir Path tempDir) throws Exception {
        File zipFile = createZip(tempDir.resolve("malicious.zip"), entryName);
        Path unzipPath = tempDir.resolve("unzipFolder");

        IOException exception = assertThrows(IOException.class, () -> FileHelper.unzipFile(zipFile, unzipPath));
        assertTrue(exception.getMessage().contains("outside of the target directory"), exception.getMessage());
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    }

    private static File createZip(Path zipPath, String entryName) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(ZIP_ENTRY_CONTENT);
            zos.closeEntry();
        }
        return zipPath.toFile();
    }
}
