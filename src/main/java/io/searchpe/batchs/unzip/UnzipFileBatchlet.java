package io.searchpe.batchs.unzip;

import io.searchpe.batchs.BatchConstants;
import io.searchpe.batchs.BatchUtils;
import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Named
public class UnzipFileBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(UnzipFileBatchlet.class);

    @Inject
    @BatchProperty
    private File workingDirectory;

    @Override
    public String process() throws Exception {
        Path zipDirPath = BatchUtils.getWorkingPath(getWorkingDirectory(), BatchConstants.BATCH_DOWNLOADS_FOLDER);
        Path unzipDirPath = BatchUtils.getWorkingPath(getWorkingDirectory(), BatchConstants.BATCH_UNZIP_FOLDER);

        logger.infof("Unzipping %s content into %s", zipDirPath.getFileName(), unzipDirPath.getFileName());
        unzipFile(zipDirPath, unzipDirPath);
        logger.infof("Unzip finished");

        return BatchStatus.COMPLETED.toString();
    }

    protected void unzipFile(Path zipDirPath, Path unzipPath) throws IOException {
        File zipDir = zipDirPath.toFile();
        File[] listOfFiles = zipDir.listFiles();
        if (listOfFiles != null) {
            for (File zipFile : listOfFiles) {
                FileUtils.unzipFile(zipFile, unzipPath);
            }
        }
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

}
