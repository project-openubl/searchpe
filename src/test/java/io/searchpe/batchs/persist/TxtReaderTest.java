package io.searchpe.batchs.persist;

import io.searchpe.batchs.BatchConstants;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class TxtReaderTest {

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Before
    public void before() throws IOException {
        File root = testFolder.getRoot();
        File zipFilesDir = testFolder.newFolder(BatchConstants.BATCH_UNZIP_FOLDER);

        File zipFile = new File("utils/padron_reducido_ruc.txt");
        FileUtils.copyToDirectory(zipFile, zipFilesDir);

        Assert.assertEquals(1, zipFilesDir.listFiles().length);
    }

    @Test
    public void emulateBatch() throws Exception {
        TxtReader txtReader = new TxtReader();
        txtReader.setWorkingDirectory(testFolder.getRoot());

        // Open
        txtReader.open(null);

        // ReadItem
        long count = 0L;

        Object object;
        while ((object = txtReader.readItem()) != null) {
            count++;
        }

        // Close
        txtReader.close();

        Assert.assertEquals(1_001L, count);
    }

    @Test
    public void emulateBatchSkip() throws Exception {
        TxtReader txtReader = new TxtReader();
        txtReader.setWorkingDirectory(testFolder.getRoot());
        txtReader.setSkip(1000L);

        // Open
        txtReader.open(null);

        // ReadItem
        long count = 0L;

        Object object;
        while ((object = txtReader.readItem()) != null) {
            count++;
        }

        // Close
        txtReader.close();

        Assert.assertEquals(1L, count);
    }

}