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
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.quartz.*;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import java.util.UUID;

@Dependent
@RegisterForReflection
public class DeleteVersionProgrammaticallyScheduler {

    @Inject
    Scheduler quartz;

    public void schedule(Long versionId) throws SchedulerException {
        String jobName = UUID.randomUUID().toString();

        JobKey jobKey = JobKey.jobKey(jobName, SearchpeJobs.VERSION_ENTITY_JOB_GROUP);
        JobDetail job = JobBuilder.newJob(DeleteVersionProgrammaticallyJob.class)
                .withIdentity(jobKey)
                .build();

        TriggerKey triggerKey = TriggerKey.triggerKey(jobName, SearchpeJobs.VERSION_ENTITY_TRIGGER_DELETE_PROGRAMMATICALLY);
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .usingJobData(DeleteVersionProgrammaticallyJob.VERSION_ID, String.valueOf(versionId))
                .startNow()
                .build();

        quartz.scheduleJob(job, trigger);
    }
}
