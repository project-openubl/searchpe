package io.searchpe.batchs.unzip;

import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class UnzipFileBatchlet implements Batchlet {

    private static final Logger logger = Logger.getLogger(UnzipFileBatchlet.class);

    @Inject
    @BatchProperty
    private String fileLocation;

    @Inject
    @BatchProperty
    private String unzipFileLocation;

    @Override
    public String process() throws Exception {
        logger.infof("Unzipping file %s into %s", fileLocation, unzipFileLocation);

        FileUtils.unzipFile(fileLocation, unzipFileLocation);

        logger.infof("File has been unzipped");
        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {
        // Nothing to do
    }

}
