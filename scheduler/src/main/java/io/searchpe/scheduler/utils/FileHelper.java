package io.searchpe.scheduler.utils;

import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     */
    public static void unzipFile(File zipFile, Path destinationPath) throws IOException {
        if (!zipFile.exists() || zipFile.isDirectory()) {
            throw new IOException("Zip file not found or is a directory");
        }

        if (!destinationPath.toFile().exists()) {
            Files.createDirectories(destinationPath);
        }

        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = destinationPath.resolve(zipEntry.getName()).toFile();
                logger.debugf("Unzipping to %s", newFile.getAbsolutePath());

                //create directories for sub directories in zip
                new File(newFile.getParent()).mkdirs();
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
