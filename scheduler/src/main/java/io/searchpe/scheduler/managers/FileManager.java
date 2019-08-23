package io.searchpe.scheduler.managers;

import io.searchpe.scheduler.utils.FileHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.UUID;

@ApplicationScoped
public class FileManager {

    private static final Logger LOGGER = Logger.getLogger(FileManager.class);

    @ConfigProperty(name = "searchpe.scheduler.workingDirectory", defaultValue = "schedulerDownloadFolder")
    String workingDirectory;

    @ConfigProperty(name = "searchpe.scheduler.sunatZipURL")
    String zipURL;

    @ConfigProperty(name = "searchpe.scheduler.connectionTimeout", defaultValue = "100000")
    Integer connectionTimeout;

    @ConfigProperty(name = "searchpe.scheduler.readTimeout", defaultValue = "100000")
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
