package io.searchpe.batchs.unzip;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.batch.runtime.BatchStatus;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UnzipFileBatchletTest {

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void test_shouldProcess() throws Exception {
        UnzipFileBatchlet batchlet = spy(new UnzipFileBatchlet());
        batchlet.setFileName("myFile.zip");
        batchlet.setOutputDir(testFolder.getRoot().getAbsolutePath());

        doNothing()
                .when(batchlet)
                .unzipFile(any(String.class), any(String.class));

        assertEquals(BatchStatus.COMPLETED.toString(), batchlet.process());

        verify(batchlet, times(1)).unzipFile(any(String.class), any(String.class));;
    }

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