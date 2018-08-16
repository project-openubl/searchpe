package io.searchpe.batchs.clean;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.batch.runtime.BatchStatus;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class CleanFilesBatchletTest {

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void test_shouldDeleteFiles() throws Exception {
        final String[] fileNames = new String[]{"file1.txt", "file2.txt", "file3.txt"};
        final String[] filesLocation = new String[3];
        for (int i = 0; i < fileNames.length; i++) {
            File file = testFolder.newFile(fileNames[i]);
            FileUtils.writeByteArrayToFile(file, new byte[]{1, 2, 3});

            filesLocation[i] = file.getAbsolutePath();
            assertTrue(file.exists());
        }


        final CleanFilesBatchlet cleanFilesBatchlet = new CleanFilesBatchlet();
        cleanFilesBatchlet.setFiles(filesLocation);

        assertEquals(BatchStatus.COMPLETED.toString(), cleanFilesBatchlet.process());
        for (String f : filesLocation) {
            assertFalse(Files.exists(Paths.get(f)));
        }
    }

    @Test
    public void test_shouldDoNotNothingIfFilesIsNull() throws Exception {
        final CleanFilesBatchlet cleanFilesBatchlet = new CleanFilesBatchlet();
        assertEquals(BatchStatus.COMPLETED.toString(), cleanFilesBatchlet.process());
    }
}