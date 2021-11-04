/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Eclipse Public License - v 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.searchpe.jobs.ingest;

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
public class IngestDataCronScheduler {

    private static final Logger logger = Logger.getLogger(IngestDataCronScheduler.class);

    @Inject
    Scheduler quartz;

    @Inject
    UserTransaction tx;

    @ConfigProperty(name = "searchpe.scheduled.cron")
    String cronRegex;

    @Scheduled(cron = "0 15 10 15 * ?")
    void schedule() {
    }

    public void schedule(@Observes StartupEvent ev) {
        try {
            tx.begin();

            JobKey jobKey = JobKey.jobKey(SearchpeJobs.VERSION_ENTITY_IMPORT_JOB, SearchpeJobs.VERSION_ENTITY_JOB_GROUP);
            JobDetail job = JobBuilder.newJob(IngestDataCronJob.class)
                    .withIdentity(jobKey)
                    .build();

            TriggerKey triggerKey = TriggerKey.triggerKey(SearchpeJobs.VERSION_ENTITY_TRIGGER_IMPORT_CRON, SearchpeJobs.VERSION_ENTITY_TRIGGER_GROUP);
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(cronSchedule(cronRegex))
                    .build();

            try {
                if (!quartz.checkExists(triggerKey)) {
                    quartz.scheduleJob(job, trigger);
                }
            } catch (SchedulerException e) {
                logger.error("Couldn't configure the CRON for importing data");
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