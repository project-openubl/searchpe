package io.searchpe.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtils {

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
}
