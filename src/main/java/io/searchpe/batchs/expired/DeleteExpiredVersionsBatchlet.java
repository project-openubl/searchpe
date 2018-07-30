package io.searchpe.batchs.expired;

import io.searchpe.model.Version;
import io.searchpe.services.VersionService;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Named
public class DeleteExpiredVersionsBatchlet implements Batchlet {

    private static final Logger logger = Logger.getLogger(DeleteExpiredVersionsBatchlet.class);

    @Inject
    @BatchProperty
    private Integer expirationTimeInMillis;

    @Inject
    private VersionService versionService;

    @Override
    public String process() throws Exception {
        logger.infof("--------------------------------------");
        logger.infof("--------------------------------------");
        logger.infof("Deleting expired versions using expiration[%s]", expirationTimeInMillis);

        if (expirationTimeInMillis != null && expirationTimeInMillis > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MILLISECOND, 0 - expirationTimeInMillis);
            Date date = calendar.getTime();

            List<Version> expiredVersions = versionService.getVersionsBefore(date);
            for (Version version : expiredVersions) {
                logger.infof("Deleting version id[%s], number[%s], date[%s]", version.getId(), version.getNumber(), version.getDate());
                versionService.deleteVersion(version);
            }
        }

        BatchStatus batchStatus = BatchStatus.COMPLETED;
        logger.infof("Batch %s finished BatchStatus[%s]", DeleteExpiredVersionsBatchlet.class.getSimpleName(), batchStatus);

        return batchStatus.toString();
    }

    @Override
    public void stop() throws Exception {

    }
}
