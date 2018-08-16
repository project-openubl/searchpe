package io.searchpe.batchs.download;

import io.searchpe.batchs.BatchConstants;
import org.jboss.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Named
public class DownloadFileBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(DownloadFileBatchlet.class);

    private String url;
    private String output;
    private Integer readTimeout;
    private Integer connectionTimeout;

    @Override
    public String process() throws Exception {
        if (getUrl() == null) {
            throw new URLNotDefinedException("URL not defined");
        }
        URL url = new URL(getUrl());

        if (getConnectionTimeout() == null) {
            setConnectionTimeout(10_000);
        }
        if (getReadTimeout() == null) {
            setReadTimeout(10_000);
        }

        Path outputFilePath = Paths.get(getOutput() != null ? getOutput() : BatchConstants.DEFAULT_DOWNLOAD_FOLDER, UUID.randomUUID().toString());
        downloadFile(url, outputFilePath.toFile(), getConnectionTimeout(), getReadTimeout());
        return BatchStatus.COMPLETED.toString();
    }

    protected void downloadFile(URL url, File outputFile, int connectionTimeout, int readTimeout) throws IOException {
        org.apache.commons.io.FileUtils.copyURLToFile(url, outputFile, connectionTimeout, readTimeout);
    }

    @Inject
    @BatchProperty
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Inject
    @BatchProperty
    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    @Inject
    @BatchProperty
    public Integer getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
    }

    @Inject
    @BatchProperty
    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }


}
