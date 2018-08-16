package io.searchpe.batchs;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BatchUtils {

    private BatchUtils() {
        // Just static methods
    }

    public static Path getWorkingPath(File baseDirectory, String subFolder) {
        if (baseDirectory == null) {
            baseDirectory = Paths.get("").toFile();
        }
        return baseDirectory.toPath().resolve(subFolder);
    }

}
