package io.searchpe.batchs.unzip;

import io.searchpe.batchs.download.DownloadFileBatchlet;
import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class UnzipFileBatchlet implements Batchlet {

    private static final Logger logger = Logger.getLogger(DownloadFileBatchlet.class);

    @Inject
    @BatchProperty
    private String fileLocation;

    @Inject
    @BatchProperty
    private String unzipFileLocation;

    @Override
    public String process() throws Exception {
        logger.infof("--------------------------------------");
        logger.infof("--------------------------------------");
        logger.infof("Unzipping %s into: %s", fileLocation, unzipFileLocation);

        FileUtils.unzipFile(fileLocation, unzipFileLocation);

        BatchStatus batchStatus = BatchStatus.COMPLETED;
        logger.infof("Batch %s finished BatchStatus[%s]", UnzipFileBatchlet.class.getSimpleName(), batchStatus);

        return batchStatus.toString();
    }

    @Override
    public void stop() throws Exception {

    }

}
