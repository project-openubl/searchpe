/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.searchpe.jobs.clean;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.RunOptions;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.UUID;

import static org.quartz.CronScheduleBuilder.cronSchedule;

@ApplicationScoped
@RegisterForReflection
public class DeleteVersionsScheduler {

    private static final Logger logger = Logger.getLogger(DeleteVersionsScheduler.class);

    JobKey cronJobKey = JobKey.jobKey(DeleteVersionsCronJob.class.getName(), "version");
    JobKey programmaticallyJobKey = JobKey.jobKey(DeleteVersionsProgrammaticallyJob.class.getName(), "version");

    @Inject
    Scheduler quartz;

    @ConfigProperty(name = "searchpe.scheduled.cron-clean")
    String cronRegex;

    public void scheduleProgrammatically(Long versionId) throws SchedulerException {
        JobDetail programmaticallyJobDetail = JobBuilder
                .newJob(DeleteVersionsProgrammaticallyJob.class)
                .withIdentity(programmaticallyJobKey)
                .storeDurably()
                .build();
        if (!quartz.checkExists(programmaticallyJobDetail.getKey())) {
            quartz.addJob(programmaticallyJobDetail, false);
        }

        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(programmaticallyJobKey)
                .withIdentity(TriggerKey.triggerKey(UUID.randomUUID().toString(), "version"))
                .usingJobData(DeleteVersionsProgrammaticallyJob.VERSION_ID, String.valueOf(versionId))
                .startNow()
                .build();

        quartz.scheduleJob(trigger);
    }

    @Scheduled(cron = "0 15 10 15 * ?")
    protected void schedule() {
    }

    protected void initJobs(@Observes StartupEvent ev) {
        QuarkusTransaction.run(QuarkusTransaction.runOptions()
                .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                .semantic(RunOptions.Semantic.DISALLOW_EXISTING), () -> {

            JobDetail cronJobDetail = JobBuilder.newJob(DeleteVersionsCronJob.class)
                    .withIdentity(cronJobKey)
                    .storeDurably()
                    .build();

            try {
                if (!quartz.checkExists(cronJobDetail.getKey())) {
                    quartz.addJob(cronJobDetail, false);
                }

                Trigger cronTrigger = TriggerBuilder.newTrigger()
                        .forJob(cronJobKey)
                        .withIdentity(TriggerKey.triggerKey(DeleteVersionsScheduler.class.getName(), "version"))
                        .withSchedule(cronSchedule(cronRegex))
                        .build();
                if (!quartz.checkExists(cronTrigger.getKey())) {
                    quartz.scheduleJob(cronTrigger);
                }
            } catch (SchedulerException e) {
                logger.error(e);
                throw new RuntimeException(e);
            }
        });
    }
}
