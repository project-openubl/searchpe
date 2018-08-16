package io.searchpe.batchs.unzip;

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
public class UnzipFileBatchletUnitTest {

    @Spy
    private UnzipFileBatchlet batchlet = new UnzipFileBatchlet();

    @Test
    public void shouldDownloadFile() throws Exception {
//        String output = "myUnzipFile.txt";
//        Path path = Paths.get(output);
//
//        Assert.assertFalse(Files.exists(path));
//
//        Mockito.when(batchlet.getFileName()).thenReturn("padron_reducido_ruc.zip");
//        Mockito.when(batchlet.getOutput()).thenReturn(output);
//        String processResult = batchlet.process();
//        Mockito.verify(batchlet, Mockito.atLeastOnce()).getFileName();
//        Mockito.verify(batchlet, Mockito.atLeastOnce()).getOutput();
//
//        Assert.assertEquals(BatchStatus.COMPLETED.toString(), processResult);
//        Assert.assertTrue(Files.exists(path));
//
//        Files.delete(path);
    }

}