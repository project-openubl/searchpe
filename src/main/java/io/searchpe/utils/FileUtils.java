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
        for (int i = 0; i < files.length; i++) {
            Path path = Paths.get(files[i]);
            if (Files.exists(path)) {
                Files.delete(path);
            }
        }
    }

    public static void downloadFile(String url, String destination) throws IOException {
        org.apache.commons.io.FileUtils.copyURLToFile(new URL(url), new File(destination), 10000, 10000);
    }

    public static void unzipFile(String zipFile, String unzipLocation) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(unzipLocation);
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    zipEntry = zis.getNextEntry();
                } finally {
                    if (fos != null) {
                        fos.close();
                    }
                }
            }
            zis.closeEntry();
            zis.close();
        } finally {
            logger.debug("Unzip finished");
        }
    }

}
