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
import org.apache.commons.io.FilenameUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ApplicationScoped
public class FileService {

    private static final Logger LOGGER = Logger.getLogger(FileService.class);

    @ConfigProperty(name = "searchpe.workspace.directory")
    String workingDirectory;

    @ConfigProperty(name = "searchpe.sunat.padronReducidoUrl")
    String zipURL;

    @ConfigProperty(name = "searchpe.sunat.connectTimeout")
    Duration connectTimeout;

    @ConfigProperty(name = "searchpe.sunat.readTimeout")
    Duration readTimeout;

    /**
     * Downloads the padrón into the workspace. Gives up if the connection can't be established within
     * {@code connectTimeout}, or if the server stops sending data for longer than {@code readTimeout}. A slow but
     * steady transfer isn't cut off: only {@link VersionService#IMPORT_TIMEOUT} bounds the whole import. A partially
     * downloaded file is deleted.
     */
    public File downloadFile() throws IOException {
        Path workingDirectoryPath = Files.createDirectories(Path.of(workingDirectory));
        Path targetZipFile = workingDirectoryPath.resolve(UUID.randomUUID() + ".zip");

        LOGGER.infof("Downloading %s into %s", zipURL, targetZipFile);
        HttpURLConnection connection = (HttpURLConnection) URI.create(zipURL).toURL().openConnection();
        connection.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
        connection.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Downloading " + zipURL + " failed with HTTP " + status);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                long bytes = Files.copy(inputStream, targetZipFile);
                LOGGER.infof("Downloaded %d bytes into %s", bytes, targetZipFile);
            }
        } catch (IOException e) {
            Files.deleteIfExists(targetZipFile);
            throw e;
        } finally {
            connection.disconnect();
        }

        return targetZipFile.toFile();
    }

    /**
     * Opens the first {@code .txt} entry of the downloaded zip, which is the padrón, without extracting it to disk.
     *
     * @return a reader decoding the padrón with {@link DataHelper#PADRON_CHARSET}; closing it closes the zip
     */
    public BufferedReader openPadron(File zipFile) throws IOException {
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile.toPath())));
        boolean handedOver = false;
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && "txt".equalsIgnoreCase(FilenameUtils.getExtension(entry.getName()))) {
                    LOGGER.infof("Reading %s from %s", entry.getName(), zipFile);
                    handedOver = true;
                    return new BufferedReader(new InputStreamReader(zip, DataHelper.PADRON_CHARSET));
                }
            }
            throw new IOException("Could not find any *.txt file in " + zipFile);
        } finally {
            if (!handedOver) {
                zip.close();
            }
        }
    }
}
