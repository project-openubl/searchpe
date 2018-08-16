package io.searchpe.batchs.unzip;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;

import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class UnzipFileBatchletTest {

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Test(expected = ZipFileNotDefinedException.class)
    public void test_shouldThrowExceptionIfNoFileNameDefined() throws Exception {
        UnzipFileBatchlet batchlet = new UnzipFileBatchlet();
        batchlet.process();
    }

    @Test
    public void test_shouldThrowExceptionIfOutputDirIsNotDirectoryOrHasPreviousContent() throws Exception {
        UnzipFileBatchlet batchlet = new UnzipFileBatchlet();
        batchlet.setFileName("myFile.zip");
        batchlet.setOutputDir(testFolder.newFile("myOutputFile.txt").getAbsolutePath());

        boolean exceptionCatch = false;
        try {
            batchlet.process();
        } catch (UnzipProcessException e) {
            exceptionCatch = true;
        }
        assertTrue(exceptionCatch);


        File previousFile = testFolder.newFile("previousFile.zip");
        FileUtils.writeByteArrayToFile(previousFile, new byte[]{1, 2, 3});
        assertTrue(previousFile.exists());
        batchlet.setOutputDir(testFolder.getRoot().getAbsolutePath());

        exceptionCatch = false;
        try {
            batchlet.process();
        } catch (UnzipProcessException e) {
            exceptionCatch = true;
        }
        assertTrue(exceptionCatch);
    }

}