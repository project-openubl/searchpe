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

import io.github.project.openubl.searchpe.services.VersionService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.RunOptions;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.inject.Inject;
import javax.transaction.UserTransaction;

@RegisterForReflection
public class DeleteVersionsProgrammaticallyJob implements Job {

    public static final String VERSION_ID = "versionId";

    @Inject
    UserTransaction tx;

    @Inject
    VersionService versionService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long versionId = Long.valueOf((String) context.getTrigger().getJobDataMap().get(VERSION_ID));

        QuarkusTransaction.run(
                QuarkusTransaction.runOptions()
                        .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                        .semantic(RunOptions.Semantic.DISALLOW_EXISTING),
                () -> {
                    versionService.deleteVersion(versionId);
                }
        );
    }

}
