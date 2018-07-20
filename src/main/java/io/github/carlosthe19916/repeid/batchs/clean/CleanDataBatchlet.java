package io.github.carlosthe19916.repeid.batchs.clean;

import io.github.carlosthe19916.repeid.batchs.BatchConstants;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Named
public class CleanDataBatchlet implements Batchlet {

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

        deleteFile(downloadedFileName);
        deleteFile(unzipFileName);

        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {

    }

    private void deleteFile(String file) throws IOException {
        Path path = Paths.get(file);
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

}
