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

import io.github.project.openubl.searchpe.jobs.SearchpeJobs;
import io.quarkus.runtime.annotations.RegisterForReflection;
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
public class IngestDataProgrammaticallyScheduler {

    @Inject
    Scheduler quartz;

    public void schedule(Long versionId) throws SchedulerException {
        String jobName = UUID.randomUUID().toString();

        JobKey jobKey = JobKey.jobKey(jobName, SearchpeJobs.VERSION_ENTITY_JOB_GROUP);
        JobDetail job = JobBuilder.newJob(IngestDataProgrammaticallyJob.class)
                .withIdentity(jobKey)
                .build();

        TriggerKey triggerKey = TriggerKey.triggerKey(jobName, SearchpeJobs.VERSION_ENTITY_TRIGGER_IMPORT_PROGRAMMATICALLY);
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .usingJobData(IngestDataProgrammaticallyJob.VERSION_ID, String.valueOf(versionId))
                .startNow()
                .build();

        quartz.scheduleJob(job, trigger);
    }

}
