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

import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.services.VersionService;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jboss.logging.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Date;

@RegisterForReflection
public class DeleteVersionsCronJob implements Job {

    private static final Logger logger = Logger.getLogger(DeleteVersionsCronJob.class);

    @Inject
    VersionService versionService;

    @Inject
    VersionRepository versionRepository;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.info("Starting cleaning of obsolete versions");
        Date staleBefore = Date.from(Instant.now().minus(VersionService.IMPORT_TIMEOUT));
        // Each deletion runs in its own transaction, so one failure doesn't roll back the others
        versionRepository.listObsolete(staleBefore).forEach(version -> {
            logger.infof("Deleting Version %s in status %s", version.id, version.status);
            try {
                versionService.deleteVersion(version.id);
            } catch (RuntimeException e) {
                logger.errorf(e, "Could not delete Version %s", version.id);
            }
        });
    }
}
