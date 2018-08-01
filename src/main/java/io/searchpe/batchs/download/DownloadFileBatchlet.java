package io.searchpe.batchs.download;

import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class DownloadFileBatchlet implements Batchlet {

    private static final Logger logger = Logger.getLogger(DownloadFileBatchlet.class);

    @Inject
    @BatchProperty
    private String url;

    @Inject
    @BatchProperty
    private String output;

    @Override
    public String process() throws Exception {
        logger.infof("Downloading file: %s into %s", getUrl(), getOutput());

        FileUtils.downloadFile(getUrl(), getOutput());

        logger.infof("File has been downloaded");
        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {
        // Nothing to do
    }

    public String getUrl() {
        return url;
    }

    public String getOutput() {
        return output;
    }

}
