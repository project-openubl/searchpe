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
import java.util.Optional;

@Named
public class DeleteIncompleteVersionsBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(DeleteIncompleteVersionsBatchlet.class);

    private Boolean deleteIncompleteVersions;
    private VersionService versionService;

    @Override
    public String process() throws Exception {
        if (Optional.ofNullable(getDeleteIncompleteVersions()).orElse(false)) {
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

    @Inject
    @BatchProperty
    public Boolean getDeleteIncompleteVersions() {
        return deleteIncompleteVersions;
    }

    public void setDeleteIncompleteVersions(Boolean deleteIncompleteVersions) {
        this.deleteIncompleteVersions = deleteIncompleteVersions;
    }

    @Inject
    public VersionService getVersionService() {
        return versionService;
    }

    public void setVersionService(VersionService versionService) {
        this.versionService = versionService;
    }
}
