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

    @Inject
    @BatchProperty
    private Integer expirationTimeInMillis;

    @Inject
    private VersionService versionService;

    @Override
    public String process() throws Exception {
        if (expirationTimeInMillis != null && expirationTimeInMillis > 0) {
            Date expirationDate = DateUtils.addMilliseconds(new Date(), Math.negateExact(expirationTimeInMillis));

            List<Version> expiredVersions = versionService.getCompleteVersionsBefore(expirationDate);
            for (int i = 0; i < expiredVersions.size(); i++) {
                Version version = expiredVersions.get(i);
                if (i == 0) {
                    logger.infof("Deletion skip on version id[%s], number[%s], date[%s] in order to maintain at least one version", version.getId(), version.getNumber(), version.getDate());
                } else {
                    logger.infof("Deleting version id[%s], number[%s], date[%s]", version.getId(), version.getNumber(), version.getDate());
                    versionService.deleteVersion(version);
                }
            }
        }
        return BatchStatus.COMPLETED.toString();
    }

}
