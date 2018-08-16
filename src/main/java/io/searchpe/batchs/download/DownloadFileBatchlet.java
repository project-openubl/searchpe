package io.searchpe.batchs.download;

import io.searchpe.batchs.BatchConstants;
import io.searchpe.batchs.BatchUtils;
import org.jboss.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;

@Named
public class DownloadFileBatchlet extends AbstractBatchlet {

    public static final Logger logger = Logger.getLogger(DownloadFileBatchlet.class);

    public static final int DEFAULT_READ_TIMEOUT = 10_000;
    public static final int DEFAULT_CONNECTION_TIMEOUT = 10_000;

    @Inject
    @BatchProperty
    private URL url;

    @Inject
    @BatchProperty
    private File workingDirectory;

    @Inject
    @BatchProperty
    private Integer readTimeout;

    @Inject
    @BatchProperty
    private Integer connectionTimeout;

    @Override
    public String process() throws Exception {
        if (getUrl() == null) {
            throw new URLNotDefinedException("URL not defined");
        }

        if (getReadTimeout() == null) {
            setReadTimeout(DEFAULT_READ_TIMEOUT);
        }
        if (getConnectionTimeout() == null) {
            setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);
        }

        File downloadFile = BatchUtils.getWorkingPath(workingDirectory, BatchConstants.BATCH_DOWNLOADS_FOLDER)
                .resolve(UUID.randomUUID().toString())
                .toFile();

        logger.infof("Downloading %s into %s", getUrl(), downloadFile.getName());
        downloadFile(getUrl(), downloadFile, getConnectionTimeout(), getReadTimeout());
        logger.infof("Download finished");

        return BatchStatus.COMPLETED.toString();
    }

    protected void downloadFile(URL url, File outputFile, int connectionTimeout, int readTimeout) throws IOException {
        org.apache.commons.io.FileUtils.copyURLToFile(url, outputFile, connectionTimeout, readTimeout);
    }

    protected URL getUrl() {
        return url;
    }

    protected void setUrl(URL url) {
        this.url = url;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Integer getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

}
