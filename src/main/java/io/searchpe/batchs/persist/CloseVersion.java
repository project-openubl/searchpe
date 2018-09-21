package io.searchpe.batchs.persist;

import io.searchpe.model.Version;
import io.searchpe.services.VersionService;
import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.Metric;
import javax.batch.runtime.context.StepContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.UserTransaction;
import java.util.HashMap;
import java.util.Map;

@Named
public class CloseVersion implements Batchlet {

    private static final Logger logger = Logger.getLogger(CloseVersion.class);

    @Inject
    private VersionService versionService;

    @Inject
    private StepContext stepContext;

    @Inject
    @BatchProperty
    protected String versionId;

    @Resource
    private UserTransaction userTransaction;

    @Override
    public String process() throws Exception {
        userTransaction.begin();

        BatchStatus batchStatus = stepContext.getBatchStatus();

        Version version = versionService.getVersion(versionId).orElseThrow(IllegalAccessException::new);
        version.setComplete(true);
        versionService.updateVersion(version);

        logger.infof("Version number[%s] updated with complete[%s]", version.getNumber(), version.isComplete());

        userTransaction.commit();

        return "SUCCESS";
    }

    @Override
    public void stop() throws Exception {

    }

}
