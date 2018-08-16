package io.searchpe.batchs.download;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import javax.batch.runtime.BatchStatus;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DownloadFileBatchletUnitTest {

    public static final String URL = "https://raw.githubusercontent.com/searchpe/searchpe/master/padron_reducido_ruc.zip";

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Spy
    private DownloadFileBatchlet downloadBatchlet = new DownloadFileBatchlet();

    @Test(expected = URLNotDefinedException.class)
    public void test_shouldThrowExceptionIfUrlIsNotDefined() throws Exception {
        String processResult = downloadBatchlet.process();
    }

    @Test
    public void shouldDownloadFile() throws Exception {
        File downloadsFolder = testFolder.newFolder("myDownloads");

        when(downloadBatchlet.getUrl()).thenReturn(URL);
        when(downloadBatchlet.getOutputFolder()).thenReturn(downloadsFolder.getAbsolutePath());

        String processResult = downloadBatchlet.process();

        String url = verify(downloadBatchlet, atLeastOnce()).getUrl();
        String outputFolder = verify(downloadBatchlet, atLeastOnce()).getOutputFolder();

        assertEquals(BatchStatus.COMPLETED.toString(), processResult);
        assertEquals(1, downloadsFolder.list().length);
    }

}