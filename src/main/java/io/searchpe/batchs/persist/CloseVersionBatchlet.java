package io.searchpe.batchs.persist;

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

@Named
public class CloseVersionBatchlet extends AbstractBatchlet {

    private static final Logger logger = Logger.getLogger(CloseVersionBatchlet.class);

    @Inject
    private VersionService versionService;

    @Inject
    @BatchProperty
    protected String versionId;

    @Resource
    private UserTransaction userTransaction;

    @Override
    public String process() throws Exception {
        userTransaction.begin();


        Version version = versionService.getVersion(versionId).orElseThrow(IllegalAccessException::new);
        version.setComplete(true);
        versionService.updateVersion(version);

        logger.infof("Version number[%s] completed", version.getNumber());


        userTransaction.commit();
        return BatchStatus.COMPLETED.toString();
    }

}
