package io.searchpe.batchs.expired;

import io.searchpe.model.Version;
import io.searchpe.services.VersionService;
import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.UserTransaction;
import java.util.List;

@Named
public class ExpiredVersionsBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(ExpiredVersionsBatchlet.class);

    @Inject
    @BatchProperty
    private Integer maxVersions;

    @Inject
    private VersionService versionService;

    @Resource
    private UserTransaction userTransaction;

    @Override
    public String process() throws Exception {
        userTransaction.begin();


        if (maxVersions != null && maxVersions > 0) {
            List<Version> expiredVersions = versionService.getCompleteVersionsDesc(maxVersions);
            for (Version version: expiredVersions) {
                versionService.deleteVersion(version);
                logger.infof("Version id[%s], number[%s], date[%s] deleted", version.getId(), version.getNumber(), version.getDate());
            }
        }


        userTransaction.commit();
        return BatchStatus.COMPLETED.toString();
    }

}
