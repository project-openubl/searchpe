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
package io.github.project.openubl.searchpe.services;

import io.github.project.openubl.searchpe.models.TipoPersona;
import io.github.project.openubl.searchpe.models.VersionEvent;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.EstadoContribuyente;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.utils.DataHelper;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class UpgradeDataService {

    private static final Logger LOGGER = Logger.getLogger(UpgradeDataService.class);

    @ConfigProperty(name = "searchpe.sunat.filter")
    Optional<List<EstadoContribuyente>> sunatFilter;

    @ConfigProperty(name = "quarkus.hibernate-orm.jdbc.statement-batch-size", defaultValue = "1000")
    Integer jdbcBatchSize;

    @Inject
    FileService fileService;

    @Inject
    UserTransaction tx;

    @Inject
    EntityManager entityManager;

    @Inject
    Event<VersionEvent.DownloadingEvent> downloadingVersionEvent;

    @Inject
    Event<VersionEvent.UnzippingFileEvent> unzippingVersionEvent;

    @Inject
    Event<VersionEvent.ImportingDataEvent> importingVersionEvent;

    @Inject
    Event<VersionEvent.RecordsDataEvent> recordsEvent;

    public void upgrade(Long versionId) {
        File downloadedFile;
        File unzippedFolder;
        File txtFile;

        // Download file
        try {
            downloadingVersionEvent.fire(() -> versionId);
            downloadedFile = fileService.downloadFile();

            unzippingVersionEvent.fire(() -> versionId);
            unzippedFolder = fileService.unzip(downloadedFile);

            txtFile = fileService.getFirstTxtFileFound(unzippedFolder.listFiles());
        } catch (IOException e) {
            LOGGER.error(e);
            return;
        }

        // Persist data
        try {
            importingVersionEvent.fire(() -> versionId);
            createContribuyentesFromFile(versionId, txtFile);
        } catch (IOException e) {
            LOGGER.error(e);
            return;
        }

        // Clear folders
        try {
            LOGGER.infof("Deleting directory %s", downloadedFile.toString());
            LOGGER.infof("Deleting directory %s", unzippedFolder.toString());
            downloadedFile.delete();
            FileUtils.deleteDirectory(unzippedFolder);
        } catch (IOException e) {
            LOGGER.error(e);
            return;
        }
    }

    public void createContribuyentesFromFile(Long versionId, File file) throws IOException {
        LOGGER.infof("Start importing contribuyentes");
        long startTime = Calendar.getInstance().getTimeInMillis();

        int totalCount = 0;
        int batchCount = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.ISO_8859_1))) {
            String line;
            boolean skip = true;

            int batchSize = jdbcBatchSize;

            tx.begin();

            while ((line = br.readLine()) != null) {
                if (skip) {
                    skip = false;
                    continue;
                }

                String[] columns = DataHelper.readLine(line, 15);

                Optional<List<ContribuyenteEntity>> contribuyentesOptional = DataHelper.buildContribuyenteEntity(versionId, columns);
                if (contribuyentesOptional.isEmpty()) {
                    continue;
                }
                List<ContribuyenteEntity> contribuyentes = contribuyentesOptional.get();

                if (sunatFilter.isPresent()) {
                    contribuyentes = contribuyentes.stream()
                            .filter(f -> {
                                if (f.tipoPersona.equals(TipoPersona.JURIDICA)) {
                                    Optional<EstadoContribuyente> estadoContribuyente = EstadoContribuyente.fromString(f.estado);
                                    return estadoContribuyente.isPresent() && sunatFilter.get().contains(estadoContribuyente.get());
                                } else {
                                    return true;
                                }
                            })
                            .collect(Collectors.toList());
                }

                if (contribuyentes.isEmpty()) {
                    continue;
                }
                contribuyentes.forEach(entity -> entityManager.persist(entity));

                totalCount = totalCount + contribuyentes.size();
                batchCount = batchCount + contribuyentes.size();

                if (batchCount >= batchSize) {
                    batchCount = 0;

                    entityManager.flush();
                    entityManager.clear();
                    tx.commit();

                    recordsEvent.fire(new VersionEvent.DefaultRecordsDataEvent(versionId, totalCount));

                    tx.begin();
                }
            }

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | SystemException | RollbackException | HeuristicMixedException e) {
            LOGGER.error(e);
            return;
        }


        try {
            tx.begin();

            VersionEntity version = VersionEntity.findById(versionId);
            version.status = Status.COMPLETED;
            version.updatedAt = new Date();
            version.records = totalCount;

            VersionEntity.persist(version);

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException | SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                LOGGER.error(se);
            }
            return;
        }

        long endTime = Calendar.getInstance().getTimeInMillis();
        LOGGER.infof("Import contribuyentes finished successfully in " + (endTime - startTime) + " milliseconds.");
    }

}
