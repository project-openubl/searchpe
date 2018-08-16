package io.searchpe.batchs.download;

import io.searchpe.batchs.BatchConstants;
import org.jboss.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;

@Named
public class DownloadFileBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(DownloadFileBatchlet.class);

    @Inject
    @BatchProperty
    private String url;

    @Inject
    @BatchProperty
    private Integer readTimeout;

    @Inject
    @BatchProperty
    private Integer connectionTimeout;

    @Inject
    @BatchProperty
    private String outputFolder;

    @Override
    public String process() throws Exception {
        if (getUrl() == null) {
            throw new URLNotDefinedException("URL not defined");
        }

        Path outputFilePath = Paths.get(getOutputFolder() != null ? getOutputFolder() : BatchConstants.DEFAULT_DOWNLOAD_FOLDER, UUID.randomUUID().toString());

        URL url = new URL(getUrl());
        if (getConnectionTimeout() == null) {
            connectionTimeout = 10_000;
        }
        if (getReadTimeout() == null) {
            readTimeout = 10_000;
        }

        org.apache.commons.io.FileUtils.copyURLToFile(url, outputFilePath.toFile(), getConnectionTimeout(), getReadTimeout());
        return BatchStatus.COMPLETED.toString();
    }

    public String getUrl() {
        return url;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public Integer getReadTimeout() {
        return readTimeout;
    }

    public String getOutputFolder() {
        return outputFolder;
    }

}
