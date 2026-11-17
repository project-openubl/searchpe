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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

import static org.quartz.CronScheduleBuilder.cronSchedule;

/**
 * Registers cron jobs in the clustered Quartz store. It is idempotent, so every replica can call it on startup, even at
 * the same time, and it applies a changed cron expression to a trigger stored by an earlier start.
 */
@ApplicationScoped
public class CronJobScheduler {

    private static final Logger LOGGER = Logger.getLogger(CronJobScheduler.class);

    @Inject
    Scheduler quartz;

    /**
     * Stores {@code jobDetail}, replacing any previous definition, and schedules it with {@code cronExpression}. Must
     * run inside a transaction, as the Quartz store is JDBC_CMT.
     *
     * @param cronExpression a Quartz cron expression
     * @throws IllegalStateException if Quartz can't store the job or its trigger
     */
    public void schedule(JobDetail jobDetail, TriggerKey triggerKey, String cronExpression) {
        try {
            quartz.addJob(jobDetail, true);

            Trigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobDetail.getKey())
                    .withIdentity(triggerKey)
                    .withSchedule(cronSchedule(cronExpression))
                    .build();
            Trigger storedTrigger = quartz.getTrigger(triggerKey);
            if (storedTrigger == null) {
                scheduleNew(trigger);
            } else if (!hasCronExpression(storedTrigger, cronExpression)) {
                LOGGER.infof("Rescheduling %s with cron expression %s", triggerKey, cronExpression);
                quartz.rescheduleJob(triggerKey, trigger);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Could not schedule " + jobDetail.getKey(), e);
        }
    }

    private void scheduleNew(Trigger trigger) throws SchedulerException {
        try {
            quartz.scheduleJob(trigger);
        } catch (ObjectAlreadyExistsException e) {
            // Defensive: with JDBC_CMT, addJob holds Quartz's trigger lock until commit, so replicas shouldn't race here
            LOGGER.debugf("Trigger %s was scheduled by another instance", trigger.getKey());
        }
    }

    private static boolean hasCronExpression(Trigger trigger, String cronExpression) {
        return trigger instanceof CronTrigger cronTrigger && cronExpression.equals(cronTrigger.getCronExpression());
    }
}
