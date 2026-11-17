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

import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileHelper {

    private static final Logger logger = Logger.getLogger(FileHelper.class);

    private FileHelper() {
        // Just utils
    }

    /**
     * Delete files or directories.
     * In case of directories, this will delete sub folders recursively.
     * In case file does not exists, this will not thrown an exception
     *
     * @param files list of files to delete
     */
    public static void deleteFilesIfExists(String[] files) throws IOException {
        for (String f : files) {
            Path path = Paths.get(f);
            File file = path.toFile();
            if (path.toFile().exists()) {
                if (file.isDirectory()) {
                    org.apache.commons.io.FileUtils.deleteDirectory(file);
                } else {
                    Files.delete(path);
                }
            }
        }
    }

    /**
     * Unzip file
     *
     * @param zipFile         file to unzip
     * @param destinationPath folder where zipFile will be unzipped
     * @throws IOException if the zip cannot be read or one of its entries would be extracted outside
     *                     {@code destinationPath}
     */
    public static void unzipFile(File zipFile, Path destinationPath) throws IOException {
        if (!zipFile.exists() || zipFile.isDirectory()) {
            throw new IOException("Zip file not found or is a directory");
        }

        Path destinationDirectory = destinationPath.toAbsolutePath().normalize();
        Files.createDirectories(destinationDirectory);

        try (
                InputStream is = Files.newInputStream(zipFile.toPath());
                ZipInputStream zis = new ZipInputStream(is)
        ) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                Path entryPath = resolveZipEntry(destinationDirectory, zipEntry);
                logger.debugf("Unzipping to %s", entryPath);

                if (zipEntry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } finally {
            logger.debug("Unzip finished");
        }
    }

    /**
     * Resolves where a zip entry must be extracted, rejecting entries such as {@code ../../etc/passwd} that would
     * escape the destination directory ("zip slip").
     */
    private static Path resolveZipEntry(Path destinationDirectory, ZipEntry zipEntry) throws IOException {
        Path entryPath = destinationDirectory.resolve(zipEntry.getName()).normalize();
        if (!entryPath.startsWith(destinationDirectory) || entryPath.equals(destinationDirectory)) {
            throw new IOException("Zip entry is outside of the target directory: " + zipEntry.getName());
        }
        return entryPath;
    }
}
