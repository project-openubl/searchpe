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
package io.github.project.openubl.searchpe.events;

import io.github.project.openubl.searchpe.models.VersionEvent;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.util.Date;

@ApplicationScoped
public class VersionEventManager {

    private static final Logger LOGGER = Logger.getLogger(VersionEventManager.class);

    @Inject
    UserTransaction tx;

    void updateStatus(Long versionId, Status status) {
        try {
            tx.begin();

            VersionEntity version = VersionEntity.findById(versionId);
            version.status = status;
            version.updatedAt = new Date();
            version.persist();

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException |
                 SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                LOGGER.error(se);
            }
        }
    }

    public void onDownloading(@Observes VersionEvent.DownloadingEvent event) {
        updateStatus(event.getVersion(), Status.DOWNLOADING);
    }

    public void onUnzipping(@Observes VersionEvent.UnzippingFileEvent event) {
        updateStatus(event.getVersion(), Status.UNZIPPING);
    }

    public void onImportingData(@Observes VersionEvent.ImportingDataEvent event) {
        updateStatus(event.getVersion(), Status.IMPORTING);
    }

    public void onImportingData(@Observes VersionEvent.RecordsDataEvent event) {
        try {
            tx.begin();

            VersionEntity version = VersionEntity.findById(event.getVersion());
            version.records = event.getRecords();
            version.updatedAt = new Date();
            version.persist();

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException |
                 SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                LOGGER.error(se);
            }
        }
    }
}
