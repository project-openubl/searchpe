package io.searchpe.batchs.unzip;

import io.searchpe.batchs.BatchConstants;
import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;

@Named
public class UnzipFileBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(UnzipFileBatchlet.class);

    private String fileName;
    private String outputDir;

    @Override
    public String process() throws Exception {
        if (fileName == null) {
            throw new ZipFileNotDefinedException("Could not found zip file");
        }

        String outputDirAbsolutePath = getOutputDir() != null ? getOutputDir() : BatchConstants.DEFAULT_UNZIP_FOLDER;

        logger.debugf("Unzipping file %s into %s", getFileName(), outputDirAbsolutePath);
        unzipFile(getFileName(), outputDirAbsolutePath);

        return BatchStatus.COMPLETED.toString();
    }

    protected void unzipFile(String fileAbsolutePath, String outputDirAbsolutePath) throws UnzipProcessException {
        try {
            File output = new File(outputDirAbsolutePath);
            if (output.exists()) {
                if (!output.isDirectory()) {
                    throw new UnzipProcessException("Output is not a directory");
                } else if (output.list() != null && output.list().length > 0) {
                    throw new UnzipProcessException("Output directory is not empty");
                }
            }

            FileUtils.unzipFile(getFileName(), outputDirAbsolutePath);
        } catch (IOException e) {
            throw new UnzipProcessException("Could not unzip file because file", e);
        }
    }

    @Inject
    @BatchProperty
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Inject
    @BatchProperty
    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

}
