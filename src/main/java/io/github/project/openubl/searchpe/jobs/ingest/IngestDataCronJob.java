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

import io.github.project.openubl.searchpe.managers.UpgradeDataManager;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.inject.Inject;
import javax.transaction.*;

@RegisterForReflection
public class IngestDataCronJob implements Job {

    @Inject
    UserTransaction tx;

    @Inject
    UpgradeDataManager dataManager;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long versionId;

        try {
            tx.begin();

            VersionEntity version = VersionEntity.generateNew();
            version.persist();

            versionId = version.id;

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException | SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                throw new IllegalStateException(se);
            }
            return;
        }

        dataManager.upgrade(versionId);
    }

}
