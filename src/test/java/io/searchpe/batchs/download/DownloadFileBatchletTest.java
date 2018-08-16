package io.searchpe.batchs.download;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.batch.runtime.BatchStatus;
import java.io.File;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DownloadFileBatchletTest {

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Test(expected = URLNotDefinedException.class)
    public void test_shouldThrowExceptionIfUrlIsNotDefined() throws Exception {
        final DownloadFileBatchlet downloadBatchlet = new DownloadFileBatchlet();
        String processResult = downloadBatchlet.process();
    }

    @Test
    public void shouldDownloadFile() throws Exception {
        final File downloadFolder = testFolder.newFolder("myDownloads");
        final DownloadFileBatchlet downloadBatchlet = Mockito.spy(new DownloadFileBatchlet());

        downloadBatchlet.setUrl("http://myfile.zip");
        downloadBatchlet.setOutput(downloadFolder.getAbsolutePath());

        doNothing()
                .when(downloadBatchlet)
                .downloadFile(Mockito.any(URL.class), Mockito.any(File.class), Mockito.any(Integer.class), Mockito.any(Integer.class));

        assertEquals(BatchStatus.COMPLETED.toString(), downloadBatchlet.process());

        verify(downloadBatchlet, times(1))
                .downloadFile(Mockito.any(URL.class), Mockito.any(File.class), Mockito.any(Integer.class), Mockito.any(Integer.class));
    }

}