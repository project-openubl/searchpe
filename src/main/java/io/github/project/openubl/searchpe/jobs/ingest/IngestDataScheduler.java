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
package io.github.project.openubl.searchpe.jobs.ingest;

import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.services.VersionService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.RunOptions;
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
import javax.inject.Inject;
import java.util.UUID;

@ApplicationScoped
@RegisterForReflection
public class IngestDataScheduler {

    private static final Logger logger = Logger.getLogger(IngestDataScheduler.class);

    JobKey programmaticallyJobKey = JobKey.jobKey(IngestDataProgrammaticallyJob.class.getName(), "version");

    @Inject
    Scheduler quartz;

    @ConfigProperty(name = "searchpe.scheduled.cron")
    String cronRegex;

    @Inject
    VersionService versionService;

    public void scheduleProgrammatically(Long versionId) throws SchedulerException {
        JobDetail programmaticallyJobDetail = JobBuilder
                .newJob(IngestDataProgrammaticallyJob.class)
                .withIdentity(programmaticallyJobKey)
                .storeDurably()
                .build();
        if (!quartz.checkExists(programmaticallyJobDetail.getKey())) {
            quartz.addJob(programmaticallyJobDetail, false);
        }

        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(programmaticallyJobKey)
                .withIdentity(TriggerKey.triggerKey(UUID.randomUUID().toString(), "version"))
                .usingJobData(IngestDataProgrammaticallyJob.VERSION_ID, String.valueOf(versionId))
                .startNow()
                .build();

        quartz.scheduleJob(trigger);
    }

    @Scheduled(cron = "{searchpe.scheduled.cron}")
    protected void schedule() {
        Long versionId = QuarkusTransaction.call(QuarkusTransaction.runOptions()
                .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                .semantic(RunOptions.Semantic.DISALLOW_EXISTING), () -> {

            VersionEntity version = VersionEntity.generateNew();
            version.persist();

            return version.id;
        });

        versionService.importPadronReducidoIntoVersion(versionId);
    }

}
