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
import java.util.Calendar;
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
            Date expirationDate = DateUtils.addMilliseconds(new Date(), Math.negateExact(expirationTimeInMillis));;

            List<Version> expiredVersions = versionService.getVersionsBefore(expirationDate);
            for (Version version : expiredVersions) {
                logger.infof("Deleting version id[%s], number[%s], date[%s]", version.getId(), version.getNumber(), version.getDate());
                versionService.deleteVersion(version);
            }
        }
        return BatchStatus.COMPLETED.toString();
    }

}
