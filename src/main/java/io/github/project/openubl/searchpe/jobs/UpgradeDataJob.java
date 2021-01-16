package io.github.project.openubl.searchpe.jobs;

import io.github.project.openubl.searchpe.managers.UpgradeDataManager;
import io.quarkus.scheduler.Scheduled;
import org.quartz.*;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class UpgradeDataJob {

    @Inject
    Scheduler quartz;

    @Inject
    UpgradeDataManager upgradeDataManager;

    public void trigger() throws SchedulerException {
        String id = UUID.randomUUID().toString();

        JobDetail job = JobBuilder.newJob(MyJob.class)
                .withIdentity(id, "Data")
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(id, "Data")
                .startNow()
                .build();
        quartz.scheduleJob(job, trigger);
    }

    @Scheduled(cron = "{searchpe.scheduled.cron}")
    void schedule() {
        upgradeDataManager.upgrade();
    }

    void upgradeData() {
        upgradeDataManager.upgrade();
    }

    public static class MyJob implements Job {
        @Inject
        UpgradeDataJob job;

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            job.upgradeData();
        }
    }
}
