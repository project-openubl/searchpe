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

import io.github.project.openubl.searchpe.utils.FileHelper;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.UUID;

@ApplicationScoped
public class FileService {

    private static final Logger LOGGER = Logger.getLogger(FileService.class);

    @ConfigProperty(name = "searchpe.workspace.directory")
    String workingDirectory;

    @ConfigProperty(name = "searchpe.sunat.padronReducidoUrl")
    String zipURL;

    public File downloadFile() throws IOException {
        File workingDirectoryFile = new File(workingDirectory);
        Path workingDirectoryPath = workingDirectoryFile.toPath();

        if (!workingDirectoryFile.exists()) {
            boolean directoryCreated = workingDirectoryFile.mkdir();
            if (directoryCreated) {
                LOGGER.infof("Directory %s created", workingDirectoryFile.toString());
            }
        }

        // Downloading
        URL sourceZipFileURL = new URL(zipURL);

        String targetZipFileName = UUID.randomUUID() + ".zip";
        File targetZipFile = workingDirectoryPath.resolve(targetZipFileName).toFile();

        LOGGER.infof("Downloading %s into %s", sourceZipFileURL.toString(), targetZipFile);
        try (
                FileOutputStream fileOutputStream = new FileOutputStream(targetZipFile);
                ReadableByteChannel readableByteChannel = Channels.newChannel(sourceZipFileURL.openStream());
                FileChannel fileChannel = fileOutputStream.getChannel();
        ) {
            final long chunkSize = 1024 * 50;
            long position = 0;
            while (fileChannel.transferFrom(readableByteChannel, position, chunkSize) > 0) {
                position += chunkSize;
            }
        }
        LOGGER.infof("Download finished successfully");

        return targetZipFile;
    }

    public File unzip(File file) throws IOException {
        File workingDirectoryFile = new File(workingDirectory);
        Path workingDirectoryPath = workingDirectoryFile.toPath();

        String fileNameWithoutExtension = FilenameUtils.removeExtension(file.getName());
        Path unzipFolderPath = workingDirectoryPath.resolve(fileNameWithoutExtension);

        LOGGER.infof("Unzip %s into %s", file.toString(), unzipFolderPath.toString());
        FileHelper.unzipFile(file, unzipFolderPath);
        LOGGER.infof("Unzip finished successfully");

        return unzipFolderPath.toFile();
    }

    public File getFirstTxtFileFound(File[] files) throws IOException {
        LOGGER.infof("Searching for *.txt file");

        File txtFile = null;
        if (files != null) {
            for (File file : files) {
                String extension = FilenameUtils.getExtension(file.getName());
                if (extension.equalsIgnoreCase("txt")) {
                    txtFile = file;
                    break;
                }
            }
        }
        if (txtFile == null) {
            throw new IllegalStateException("Could not find any *.txt file to read");
        }

        LOGGER.infof("%s found", txtFile.toString());
        return txtFile;
    }

}
