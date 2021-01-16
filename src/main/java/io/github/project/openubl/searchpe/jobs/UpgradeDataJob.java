package io.github.project.openubl.searchpe.jobs;

import io.github.project.openubl.searchpe.managers.UpgradeDataManager;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.scheduler.Scheduled;
import org.quartz.*;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class UpgradeDataJob {

    @Inject
    Scheduler quartz;

    @Inject
    UpgradeDataManager upgradeDataManager;

    public void trigger(VersionEntity version) throws SchedulerException {
        String versionId = String.valueOf(version.id);

        String jobId = UUID.randomUUID().toString();
        JobDetail job = JobBuilder.newJob(MyJob.class)
                .withIdentity(jobId, "Data")
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobId, "Data")
                .usingJobData("versionId", versionId)
                .startNow()
                .build();
        quartz.scheduleJob(job, trigger);
    }

    @Scheduled(cron = "{searchpe.scheduled.cron}")
    void schedule() {
        Date currentTime = new Date();
        VersionEntity version = VersionEntity.Builder.aVersionEntity()
                .withActive(false)
                .withCreatedAt(currentTime)
                .withUpdatedAt(currentTime)
                .withStatus(Status.SCHEDULED)
                .build();
        version.persist();

        upgradeDataManager.upgrade(version.id);
    }

    void upgradeData(Long versionId) {
        upgradeDataManager.upgrade(versionId);
    }

    public static class MyJob implements Job {
        @Inject
        UpgradeDataJob job;

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            String versionId = (String) context.getTrigger().getJobDataMap().get("versionId");
            job.upgradeData(Long.valueOf(versionId));
        }
    }
}
