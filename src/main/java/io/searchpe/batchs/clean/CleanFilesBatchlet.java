package io.searchpe.batchs.clean;

import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Named
public class CleanFilesBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(CleanFilesBatchlet.class);

    @Inject
    @BatchProperty
    private String[] files;

    @Override
    public String process() throws Exception {
        if (getFiles() != null) {
            FileUtils.deleteFilesIfExists(getFiles());
            logger.infof("Files %s has been deleted", Stream.of(getFiles()).collect(Collectors.joining(",")));
        }

        return BatchStatus.COMPLETED.toString();
    }

    public String[] getFiles() {
        return files;
    }

}
