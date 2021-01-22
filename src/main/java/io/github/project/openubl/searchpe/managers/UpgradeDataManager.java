/**
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
package io.github.project.openubl.searchpe.managers;

import io.github.project.openubl.searchpe.models.VersionEvent;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteId;
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
import javax.transaction.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

@ApplicationScoped
public class UpgradeDataManager {

    private static final Logger LOGGER = Logger.getLogger(UpgradeDataManager.class);

    @ConfigProperty(name = "quarkus.hibernate-orm.jdbc.statement-batch-size", defaultValue = "1000")
    Integer jdbcBatchSize;

    @Inject
    FileManager fileManager;

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

    public void upgrade(Long versionId) {
        File downloadedFile;
        File unzippedFolder;
        File txtFile;

        // Download file
        try {
            downloadingVersionEvent.fire(() -> versionId);
            downloadedFile = fileManager.downloadFile();

            unzippingVersionEvent.fire(() -> versionId);
            unzippedFolder = fileManager.unzip(downloadedFile);

            txtFile = fileManager.getFirstTxtFileFound(unzippedFolder.listFiles());
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
        VersionEntity version;

        try {
            tx.begin();
            version = VersionEntity.findById(versionId);
            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException | SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                LOGGER.error(se);
            }
            return;
        }

        LOGGER.infof("Start importing contribuyentes");
        long startTime = Calendar.getInstance().getTimeInMillis();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean skip = true;

            int batchSize = jdbcBatchSize;

            tx.begin();
            int cont = 0;
            while ((line = br.readLine()) != null) {
                if (skip) {
                    skip = false;
                    continue;
                }

                String[] columns = DataHelper.readLine(line, 15);
                ContribuyenteEntity contribuyente = ContribuyenteEntity
                        .Builder.aContribuyenteEntity()
                        .withId(new ContribuyenteId(versionId, columns[0]))
                        .withRazonSocial(columns[1].toLowerCase())
                        .withEstadoContribuyente(columns[2])
                        .withCondicionDomicilio(columns[3])
                        .withUbigeo(columns[4])
                        .withTipoVia(columns[5])
                        .withNombreVia(columns[6])
                        .withCodigoZona(columns[7])
                        .withTipoZona(columns[8])
                        .withNumero(columns[9])
                        .withInterior(columns[10])
                        .withLote(columns[11])
                        .withDepartamento(columns[12])
                        .withManzana(columns[13])
                        .withKilometro(columns[14])
                        .build();

                entityManager.persist(contribuyente);
                cont++;
                if (cont % batchSize == 0) {
                    entityManager.flush();
                    entityManager.clear();
                    tx.commit();
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

            version = VersionEntity.findById(versionId);
            version.status = Status.COMPLETED;
            version.updatedAt = new Date();

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
        LOGGER.infof("Import contribuyentes finished successfully in" + (endTime - startTime) + " milliseconds.");
    }

}
