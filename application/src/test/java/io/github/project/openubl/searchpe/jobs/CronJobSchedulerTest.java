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
package io.github.project.openubl.searchpe.jobs;

import io.github.project.openubl.searchpe.AbstractBaseTest;
import io.github.project.openubl.searchpe.DefaultProfileManager;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(DefaultProfileManager.class)
public class CronJobSchedulerTest extends AbstractBaseTest {

    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("cron-job-scheduler-test", "test");

    @Inject
    CronJobScheduler cronJobScheduler;

    @Inject
    Scheduler quartz;

    @Override
    public Class<?> getTestClass() {
        return CronJobSchedulerTest.class;
    }

    @Test
    public void scheduleIsIdempotentAndAppliesANewCronExpression() throws Exception {
        schedule("0 0 1 * * ?");
        schedule("0 0 1 * * ?");
        assertEquals("0 0 1 * * ?", storedCronExpression());

        schedule("0 30 2 * * ?");
        assertEquals("0 30 2 * * ?", storedCronExpression());
    }

    private void schedule(String cronExpression) throws Exception {
        JobDetail jobDetail = JobBuilder.newJob(NoOpJob.class)
                .withIdentity("cron-job-scheduler-test", "test")
                .storeDurably()
                .build();
        QuarkusTransaction.requiringNew().call(() -> {
            cronJobScheduler.schedule(jobDetail, TRIGGER_KEY, cronExpression);
            return null;
        });
    }

    private String storedCronExpression() throws Exception {
        return ((CronTrigger) quartz.getTrigger(TRIGGER_KEY)).getCronExpression();
    }

    public static class NoOpJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
            // Never meant to run: the tests only look at the stored trigger
        }
    }
}
