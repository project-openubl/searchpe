package io.searchpe.batchs.unzip;

import io.searchpe.batchs.download.DownloadFileBatchlet;
import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        logger.info("Unzip:" + fileLocation + " into:" + unzipFileLocation);
        FileUtils.unzipFile(fileLocation, unzipFileLocation);
        logger.info("Unzip finished");
        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {

    }

}
