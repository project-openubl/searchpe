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
package io.github.project.openubl.searchpe.managers;

import io.github.project.openubl.searchpe.utils.FileHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.Dependent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.UUID;

@Dependent
public class FileManager {

    private static final Logger LOGGER = Logger.getLogger(FileManager.class);

    @ConfigProperty(name = "searchpe.workspace.directory", defaultValue = "workspace")
    String workingDirectory;

    @ConfigProperty(name = "searchpe.sunat.padronReducidoUrl")
    String zipURL;

    @ConfigProperty(name = "searchpe.workspace.connectionTimeout", defaultValue = "100000")
    Integer connectionTimeout;

    @ConfigProperty(name = "searchpe.workspace.readTimeout", defaultValue = "100000")
    Integer readTimeout;

    public File downloadFile() throws IOException {
        File workingDirectoryFile = new File(workingDirectory);
        Path workingDirectoryPath = workingDirectoryFile.toPath();

        if (!workingDirectoryFile.exists()) {
            workingDirectoryFile.mkdir();
            LOGGER.infof("Directory %s created", workingDirectoryFile.toString());
        }

        // Downloading
        String zipFileName = UUID.randomUUID().toString() + ".zip";
        File zipFile = workingDirectoryPath.resolve(zipFileName).toFile();
        URL zipFileURL = new URL(zipURL);

        LOGGER.infof("Downloading %s into %s", zipFileURL.toString(), zipFile);
        FileUtils.copyURLToFile(zipFileURL, zipFile, connectionTimeout, readTimeout);
        LOGGER.infof("Download finished successfully");

        return zipFile;
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
