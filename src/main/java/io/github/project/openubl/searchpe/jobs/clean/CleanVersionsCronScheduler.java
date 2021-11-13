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

import io.github.project.openubl.searchpe.jobs.SearchpeJobs;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.quartz.*;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.transaction.*;

import static org.quartz.CronScheduleBuilder.cronSchedule;

@Singleton
@RegisterForReflection
public class CleanVersionsCronScheduler {

    private static final Logger logger = Logger.getLogger(CleanVersionsCronScheduler.class);

    @Inject
    Scheduler quartz;

    @Inject
    UserTransaction tx;

    @ConfigProperty(name = "searchpe.scheduled.cron-clean")
    String cronRegex;

    @Scheduled(cron = "0 15 10 15 * ?")
    void schedule() {
    }

    public void schedule(@Observes StartupEvent ev) {
        try {
            tx.begin();

            JobKey jobKey = JobKey.jobKey(SearchpeJobs.VERSION_ENTITY_CLEAN_JOB, SearchpeJobs.VERSION_ENTITY_JOB_GROUP);
            JobDetail job = JobBuilder.newJob(CleanVersionsCronJob.class)
                    .withIdentity(jobKey)
                    .build();

            TriggerKey triggerKey = TriggerKey.triggerKey(SearchpeJobs.VERSION_ENTITY_TRIGGER_CLEAN_CRON, SearchpeJobs.VERSION_ENTITY_TRIGGER_GROUP);
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(cronSchedule(cronRegex))
                    .build();

            try {
                if (!quartz.checkExists(triggerKey)) {
                    quartz.scheduleJob(job, trigger);
                }
            } catch (SchedulerException e) {
                logger.error("Couldn't configure the CRON for cleaning versions");
            }

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException | SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                throw new IllegalStateException(se);
            }
            return;
        }
    }
}
