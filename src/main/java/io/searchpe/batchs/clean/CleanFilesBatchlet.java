package io.searchpe.batchs.clean;

import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
public class CleanFilesBatchlet implements Batchlet {

    private static final Logger logger = Logger.getLogger(CleanFilesBatchlet.class);

    @Inject
    @BatchProperty
    private String[] files;

    @Override
    public String process() throws Exception {
        logger.info("Cleaning files:" + files);

        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                Path path = Paths.get(files[i]);
                if (Files.exists(path)) {
                    Files.delete(path);
                }
            }
        }

        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {

    }

}
