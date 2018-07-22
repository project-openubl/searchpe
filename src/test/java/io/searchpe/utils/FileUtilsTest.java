package io.searchpe.utils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.validation.constraints.AssertTrue;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class FileUtilsTest {

    @Test
    public void deleteFilesIfExists() throws Exception {
        OutputStream stream = new FileOutputStream("file1.txt");
        try {
            stream.write(new byte[]{1, 2, 3});
        } finally {
            stream.close();
        }

        //
        String[] files = new String[]{"file1.txt", "file2.txt"};

        Assert.assertTrue(Files.exists(Paths.get("file1.txt")));
        FileUtils.deleteFilesIfExists(files);
        Assert.assertFalse(Files.exists(Paths.get("file1.txt")));
    }

    @Test
    public void downloadFile() throws Exception {
        Path path = Paths.get("myFile.zip");
        if (Files.exists(path)){
            Files.delete(path);
        }

        final String url = "https://raw.githubusercontent.com/searchpe/searchpe/master/padron_reducido_ruc.zip";
        FileUtils.downloadFile(url, path.getFileName().toString());
        Assert.assertTrue(Files.exists(path));

        Files.delete(path);
    }

    @Test
    public void unzipFile() throws Exception {
        Path path = Paths.get("myUnzipFile.txt");
        if (Files.exists(path)){
            Files.delete(path);
        }

        FileUtils.unzipFile("padron_reducido_ruc.zip", "myUnzipFile.txt");
        Assert.assertTrue(Files.exists(path));

        Files.delete(path);
    }

}