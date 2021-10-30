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
package io.github.project.openubl.searchpe.jobs.clean;

import io.github.project.openubl.searchpe.managers.VersionManager;
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.resources.VersionResource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jboss.logging.Logger;
import org.quartz.*;

import javax.inject.Inject;
import javax.transaction.*;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.quartz.CronScheduleBuilder.cronSchedule;

@RegisterForReflection
public class CleanVersionsCronJob implements Job {

    private static final Logger logger = Logger.getLogger(CleanVersionsCronJob.class);

    @Inject
    UserTransaction tx;

    @Inject
    VersionManager versionManager;

    @Inject
    VersionRepository versionRepository;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            tx.begin();

            Optional<VersionEntity> activeVersion = versionRepository.findActive();

            versionRepository.listAll().stream()
                    .filter(f -> f.status.equals(Status.COMPLETED) || f.status.equals(Status.ERROR))
                    .filter(f -> activeVersion.map(versionEntity -> !Objects.equals(versionEntity, f)).orElse(true))
                    .peek(f -> logger.info("Deleting VersionEntity:" + f.id))
                    .forEach(f -> versionManager.deleteVersion(f.id));

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
