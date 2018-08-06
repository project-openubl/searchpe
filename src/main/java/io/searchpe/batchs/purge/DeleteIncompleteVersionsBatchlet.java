package io.searchpe.batchs.purge;

import io.searchpe.model.Version;
import io.searchpe.model.VersionAttributes;
import io.searchpe.services.VersionService;
import org.jboss.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
public class DeleteIncompleteVersionsBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(DeleteIncompleteVersionsBatchlet.class);

    @Inject
    @BatchProperty
    private Boolean deleteIncompleteVersions;

    @Inject
    private VersionService versionService;

    private boolean isDeleteIncompleteVersions() {
        return getDeleteIncompleteVersions() != null && getDeleteIncompleteVersions();
    }

    @Override
    public String process() throws Exception {
        if (isDeleteIncompleteVersions()) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put(VersionAttributes.COMPLETE, false);
            List<Version> versions = getVersionService().getVersionsByParameters(parameters);

            for (Version version : versions) {
                logger.infof("Deleting version id[%s], number[%s], date[%s]", version.getId(), version.getNumber(), version.getDate());
                getVersionService().deleteVersion(version);
            }
        }
        return BatchStatus.COMPLETED.toString();
    }

    public Boolean getDeleteIncompleteVersions() {
        return deleteIncompleteVersions;
    }

    public VersionService getVersionService() {
        return versionService;
    }

}
