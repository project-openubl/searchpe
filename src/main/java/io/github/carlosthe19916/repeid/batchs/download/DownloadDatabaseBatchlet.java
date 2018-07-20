package io.github.carlosthe19916.repeid.batchs.download;

import io.github.carlosthe19916.repeid.batchs.BatchConstants;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Named
public class DownloadDatabaseBatchlet implements Batchlet {

    @Inject
    private JobContext jobContext;

    @Inject
    @BatchProperty
    private String url;

    @Override
    public String process() throws Exception {
        Properties jobContextProperties = jobContext.getProperties();
        String downloadedFileName = jobContextProperties.getProperty(BatchConstants.DOWNLOAD_FILE_NAME);
        String unzipFileName = jobContextProperties.getProperty(BatchConstants.UNZIP_FILE);

        URLConnection urlCon = new URL(url).openConnection();

        InputStream is = urlCon.getInputStream();
        FileOutputStream fos = new FileOutputStream(downloadedFileName);

        byte[] buffer = new byte[1000];
        int bytesRead = is.read(buffer);

        while (bytesRead > 0) {

            fos.write(buffer, 0, bytesRead);
            bytesRead = is.read(buffer);

        }

        is.close();
        fos.close();

        unzip(downloadedFileName, unzipFileName);
        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {

    }

    private void unzip(String fileZip, String unzipFile) throws IOException {
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
        ZipEntry zipEntry = zis.getNextEntry();
        while(zipEntry != null){
            File newFile = new File(unzipFile);
            FileOutputStream fos = new FileOutputStream(newFile);
            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
    }

}
