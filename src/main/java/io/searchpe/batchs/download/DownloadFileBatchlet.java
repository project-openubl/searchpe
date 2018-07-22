package io.searchpe.batchs.download;

import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

@Named
public class DownloadFileBatchlet implements Batchlet {

    private static final Logger logger = Logger.getLogger(DownloadFileBatchlet.class);

    @Inject
    @BatchProperty
    private String url;

    @Inject
    @BatchProperty
    private String fileLocation;

    @Override
    public String process() throws Exception {
        logger.info("Downloading:" + url);
        FileUtils.downloadFile(url, fileLocation);
        logger.info("Download finished");

        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {

    }

}
