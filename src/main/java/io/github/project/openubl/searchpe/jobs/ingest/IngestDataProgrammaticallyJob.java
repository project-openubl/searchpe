package io.github.project.openubl.searchpe.jobs.ingest;

import io.github.project.openubl.searchpe.managers.UpgradeDataManager;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.inject.Inject;
import javax.transaction.*;
import java.util.List;

@RegisterForReflection
public class IngestDataProgrammaticallyJob implements Job {

    public static final String VERSION_ID = "versionId";

    @Inject
    UpgradeDataManager dataManager;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String versionId = (String) context.getTrigger().getJobDataMap().get(VERSION_ID);
        dataManager.upgrade(Long.valueOf(versionId));
    }

}
