package io.searchpe.batchs.unzip;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.batch.runtime.BatchStatus;
import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UnzipFileBatchletTest {

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void test_shouldProcessCallingUnzip() throws Exception {
        File baseDir = testFolder.getRoot().getAbsoluteFile();

        UnzipFileBatchlet batchlet = spy(new UnzipFileBatchlet());
        batchlet.setWorkingDirectory(baseDir);

        doNothing()
                .when(batchlet)
                .unzipFile(any(Path.class), any(Path.class));

        assertEquals(BatchStatus.COMPLETED.toString(), batchlet.process());
        verify(batchlet, times(1)).unzipFile(any(Path.class), any(Path.class));
    }

}