package io.searchpe.batchs.unzip;

import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class UnzipFileBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(UnzipFileBatchlet.class);

    @Inject
    @BatchProperty
    private String fileName;

    @Override
    public String process() throws Exception {
        String outputFolder = UUID.randomUUID().toString();

        logger.debugf("Unzipping file %s into %s", getFileName(), outputFolder);
        FileUtils.unzipFile(getFileName(), outputFolder);

        return BatchStatus.COMPLETED.toString();
    }

    public String getFileName() {
        return fileName;
    }

}
