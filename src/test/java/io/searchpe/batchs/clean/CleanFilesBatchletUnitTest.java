package io.searchpe.batchs.clean;

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
public class CleanFilesBatchletUnitTest {

    @Spy
    private CleanFilesBatchlet batchlet = new CleanFilesBatchlet();

    @Test
    public void shouldDeleteFiles() throws Exception {
        String[] files = new String[]{"file1.txt", "file2.txt"};
        Path path1 = Files.write(Paths.get(files[0]), new byte[]{1, 2, 3});
        Path path2 = Files.write(Paths.get(files[0]), new byte[]{1, 2, 3});

        Assert.assertTrue(Files.exists(path1));
        Assert.assertTrue(Files.exists(path2));

        Mockito.when(batchlet.getFiles()).thenReturn(files);
        String processResult = batchlet.process();
        Mockito.verify(batchlet, Mockito.atLeastOnce()).getFiles();

        Assert.assertEquals(BatchStatus.COMPLETED.toString(), processResult);
        Assert.assertFalse(Files.exists(path1));
        Assert.assertFalse(Files.exists(path2));
    }
}