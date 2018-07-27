package io.searchpe.batchs.clean;

import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class CleanFilesBatchlet implements Batchlet {

    private static final Logger logger = Logger.getLogger(CleanFilesBatchlet.class);

    @Inject
    @BatchProperty
    private String[] files;

    @Override
    public String process() throws Exception {
        logger.infof("--------------------------------------");
        logger.infof("--------------------------------------");
        logger.infof("Deleting files if exists: %s", files);

        if (files != null) {
            FileUtils.deleteFilesIfExists(files);
        }

        BatchStatus batchStatus = BatchStatus.COMPLETED;
        logger.infof("Batch %s finished BatchStatus[%s]", CleanFilesBatchlet.class.getSimpleName(), batchStatus);

        return batchStatus.toString();
    }

    @Override
    public void stop() throws Exception {

    }

}
