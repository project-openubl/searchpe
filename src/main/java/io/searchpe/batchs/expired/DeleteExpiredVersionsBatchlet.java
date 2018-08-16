package io.searchpe.batchs.expired;

import io.searchpe.model.Version;
import io.searchpe.services.VersionService;
import io.searchpe.utils.DateUtils;
import org.jboss.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Date;
import java.util.List;

@Named
public class DeleteExpiredVersionsBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(DeleteExpiredVersionsBatchlet.class);

    private Integer expirationTimeInMillis;
    private VersionService versionService;

    @Override
    public String process() throws Exception {
        if (getExpirationTimeInMillis() != null && getExpirationTimeInMillis() > 0) {
            Date expirationDate = DateUtils.addMilliseconds(new Date(), Math.negateExact(getExpirationTimeInMillis()));

            List<Version> expiredVersions = getVersionService().getCompleteVersionsBefore(expirationDate);
            for (int i = 0; i < expiredVersions.size(); i++) {
                Version version = expiredVersions.get(i);
                if (i == 0) {
                    logger.infof("Deletion skip on version id[%s], number[%s], date[%s] in order to maintain at least one version", version.getId(), version.getNumber(), version.getDate());
                } else {
                    logger.infof("Deleting version id[%s], number[%s], date[%s]", version.getId(), version.getNumber(), version.getDate());
                    getVersionService().deleteVersion(version);
                }
            }
        }
        return BatchStatus.COMPLETED.toString();
    }

    @Inject
    @BatchProperty
    public Integer getExpirationTimeInMillis() {
        return expirationTimeInMillis;
    }

    public void setExpirationTimeInMillis(Integer expirationTimeInMillis) {
        this.expirationTimeInMillis = expirationTimeInMillis;
    }

    @Inject
    public VersionService getVersionService() {
        return versionService;
    }

    public void setVersionService(VersionService versionService) {
        this.versionService = versionService;
    }
}
