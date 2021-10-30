package io.github.project.openubl.searchpe.jobs.ingest;

import io.github.project.openubl.searchpe.managers.UpgradeDataManager;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.inject.Inject;
import javax.transaction.*;

@RegisterForReflection
public class IngestDataCronJob implements Job {

    @Inject
    UserTransaction tx;

    @Inject
    UpgradeDataManager dataManager;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long versionId;

        try {
            tx.begin();

            VersionEntity version = VersionEntity.generateNew();
            version.persist();

            versionId = version.id;

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException | SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                throw new IllegalStateException(se);
            }
            return;
        }

        dataManager.upgrade(versionId);
    }

}
