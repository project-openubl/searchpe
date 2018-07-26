package io.searchpe.batchs.purge;

import io.searchpe.model.Version;
import io.searchpe.services.CompanyService;
import io.searchpe.services.VersionService;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.NotFoundException;
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
        if (purgeIncompleteVersions != null && purgeIncompleteVersions) {
            Map<String, Object>
            versionService.getVersionsByParameters();
        }

        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {

    }
}
