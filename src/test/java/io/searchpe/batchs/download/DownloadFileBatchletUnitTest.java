package io.searchpe.batchs.download;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import javax.batch.runtime.BatchStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(MockitoJUnitRunner.class)
public class DownloadFileBatchletUnitTest {

    @Spy
    private DownloadFileBatchlet batchlet = new DownloadFileBatchlet();

    @Test
    public void shouldDownloadFile() throws Exception {
        String destiny = "myFile.zip";
        Path path = Paths.get(destiny);

        Assert.assertFalse(Files.exists(path));

        Mockito.when(batchlet.getUrl()).thenReturn("https://raw.githubusercontent.com/searchpe/searchpe/master/padron_reducido_ruc.zip");
        Mockito.when(batchlet.getOutput()).thenReturn(destiny);
        String processResult = batchlet.process();
        Mockito.verify(batchlet, Mockito.atLeastOnce()).getUrl();
        Mockito.verify(batchlet, Mockito.atLeastOnce()).getOutput();

        Assert.assertEquals(BatchStatus.COMPLETED.toString(), processResult);
        Assert.assertTrue(Files.exists(path));

        Files.delete(path);
    }

}