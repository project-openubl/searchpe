package io.searchpe.utils;

import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtils {

    private static final Logger logger = Logger.getLogger(FileUtils.class);

    private FileUtils() {
        // Just utils
    }

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

    public static void unzipFile(String zipFilePath, String destinationDirectory) throws IOException {
        File zipFile = new File(zipFilePath);
        if (!zipFile.exists() || zipFile.isDirectory()) {
            throw new IOException("Zip file not found or is a directory");
        }

        Path destinationPath = Paths.get(destinationDirectory);
        if (!Files.exists(destinationPath)) {
            Files.createDirectories(destinationPath);
        }

        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = destinationPath.resolve(zipEntry.getName()).toFile();
                logger.debugf("Unzipping to %s", newFile.getAbsolutePath());

                //create directories for sub directories in zip
                boolean mkdirs = new File(newFile.getParent()).mkdirs();
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    zipEntry = zis.getNextEntry();
                }
            }
            zis.closeEntry();
        } finally {
            logger.debug("Unzip finished");
        }
    }

}
