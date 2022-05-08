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
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.services.VersionService;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jboss.logging.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.inject.Inject;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.util.Objects;
import java.util.Optional;

@RegisterForReflection
public class DeleteVersionsCronJob implements Job {

    private static final Logger logger = Logger.getLogger(DeleteVersionsCronJob.class);

    @Inject
    UserTransaction tx;

    @Inject
    VersionService versionService;

    @Inject
    VersionRepository versionRepository;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.error("Starting cleaning of unused VersionEntities Cron");

        try {
            tx.begin();

            Optional<VersionEntity> activeVersion = versionRepository.findActive();

            versionRepository.listAll().stream()
                    .filter(f -> f.status.equals(Status.COMPLETED) || f.status.equals(Status.ERROR))
                    .filter(f -> activeVersion.map(versionEntity -> !Objects.equals(versionEntity, f)).orElse(true))
                    .peek(f -> logger.info("Deleting VersionEntity:" + f.id))
                    .forEach(f -> versionService.deleteVersion(f.id));

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException |
                 SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                throw new IllegalStateException(se);
            }
            return;
        }
    }
}
