package io.searchpe.batchs.delete;

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
    private Integer expirationVersionDays;

    @Inject
    private VersionService versionService;

    @Override
    public String process() throws Exception {
        if (expirationVersionDays != null && expirationVersionDays > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, 0 - expirationVersionDays);
            Date date = calendar.getTime();

            List<Version> expiredVersions = versionService.getVersionsBefore(date);
            for (Version version : expiredVersions) {
                versionService.deleteVersion(version);
            }
        }
        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {

    }
}
