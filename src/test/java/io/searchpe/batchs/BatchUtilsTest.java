package io.searchpe.batchs;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class BatchUtilsTest {

    @Test
    public void shoudCreateFromBaseDir() {
        Path basePath = Paths.get("baseDir");
        Path workingPath = BatchUtils.getWorkingPath(basePath.toFile(), "subFolder");
        assertEquals(basePath.resolve("subFolder"), workingPath);
    }

    @Test
    public void shouldCreateWithoutBaseDir() {
        Path path = BatchUtils.getWorkingPath(null, "folder");
        assertEquals(Paths.get("folder"), path);
    }

}