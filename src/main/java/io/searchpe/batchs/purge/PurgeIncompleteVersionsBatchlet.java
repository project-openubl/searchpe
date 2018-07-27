package io.searchpe.batchs.purge;

import io.searchpe.model.Version;
import io.searchpe.model.VersionAttributes;
import io.searchpe.services.VersionService;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
public class PurgeIncompleteVersionsBatchlet implements Batchlet {

    private static final Logger logger = Logger.getLogger(PurgeIncompleteVersionsBatchlet.class);

    @Inject
    @BatchProperty
    private Boolean purgeIncompleteVersions;

    @Inject
    private VersionService versionService;

    @Override
    public String process() throws Exception {
        logger.infof("--------------------------------------");
        logger.infof("--------------------------------------");
        logger.infof("Purging incomplete versions");

        if (purgeIncompleteVersions != null && purgeIncompleteVersions) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put(VersionAttributes.complete, false);
            List<Version> versions = versionService.getVersionsByParameters(parameters);
            for (Version version : versions) {
                logger.infof("Purging version id[%s], number[%s], date[%s]", version.getId(), version.getNumber(), version.getDate());
                versionService.deleteVersion(version);
            }
        }

        BatchStatus batchStatus = BatchStatus.COMPLETED;
        logger.infof("Batch %s finished BatchStatus[%s]", PurgeIncompleteVersionsBatchlet.class.getSimpleName(), batchStatus);

        return batchStatus.toString();
    }

    @Override
    public void stop() throws Exception {

    }
}
