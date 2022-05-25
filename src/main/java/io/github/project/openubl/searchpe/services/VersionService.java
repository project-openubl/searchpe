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

import io.github.project.openubl.searchpe.models.VersionEvent;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.EstadoContribuyente;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.utils.DataHelper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.RunOptions;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.sql.DataSource;
import javax.transaction.Transactional;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class VersionService {

    private static final Logger LOGGER = Logger.getLogger(VersionService.class);

    private static final String TABLE_COLUMNS = new StringBuilder()
            .append("version_id").append(",")
            .append("ruc").append(",")
            .append("dni").append(",")
            .append("nombre").append(",")
            .append("estado").append(",")
            .append("condicion_domicilio").append(",")
            .append("ubigeo").append(",")
            .append("tipo_via").append(",")
            .append("nombre_via").append(",")
            .append("codigo_zona").append(",")
            .append("tipo_zona").append(",")
            .append("numero").append(",")
            .append("interior").append(",")
            .append("lote").append(",")
            .append("departamento").append(",")
            .append("manzana").append(",")
            .append("kilometro")
            .toString();

    private static final String CSV_HEADER = TABLE_COLUMNS + System.lineSeparator();

    private static final String CREATE_PARTITION_SQL = "create table CONTRIBUYENTE_VERSION_%1$s partition of CONTRIBUYENTE for values from (%1$s) TO (%2$s)";
    private static final String DELETE_PARTITION_SQL = "drop table if exists CONTRIBUYENTE_VERSION_%1$s";
    private static final String CREATE_INDEX_SQL = "create index ix_contribuyente_ruc_version_%1$s on CONTRIBUYENTE using hash (ruc) where version_id=%1$s";
    private static final String DELETE_INDEX_SQL = "drop index if exists ix_contribuyente_ruc_version_%1$s";
    private static final String SELECT_INDEX_WATCH_SQL = "select count(*) from pg_stat_progress_create_index";

    @ConfigProperty(name = "searchpe.sunat.filter")
    Optional<List<EstadoContribuyente>> sunatFilter;

    @ConfigProperty(name = "searchpe.sunat.chunkSize")
    int chunkSize;

    @ConfigProperty(name = "searchpe.sunat.watchDelay")
    int watchDelay;

    @Inject
    DataSource dataSource;

    @Inject
    FileService fileService;

    @Inject
    Event<VersionEvent.DownloadingEvent> downloadingVersionEvent;

    @Inject
    Event<VersionEvent.UnzippingFileEvent> unzippingVersionEvent;

    @Inject
    Event<VersionEvent.ImportingDataEvent> importingVersionEvent;

    @Transactional
    public void deleteVersion(Long versionId) {
        String deletePartitionQuery = String.format(DELETE_PARTITION_SQL, versionId);
        String deletePartitionIndex = String.format(DELETE_INDEX_SQL, versionId);
        VersionEntity.getEntityManager()
                .createNativeQuery(deletePartitionQuery)
                .executeUpdate();
        VersionEntity.getEntityManager()
                .createNativeQuery(deletePartitionIndex)
                .executeUpdate();

        ContribuyenteEntity.delete("id.versionId", versionId);
        VersionEntity.delete("id", versionId);
    }

    @Transactional(Transactional.TxType.NEVER)
    public void importPadronReducidoIntoVersion(Long versionId) {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);

        LOGGER.info("Init import task");
        Future<?> importTask = executorService.submit(() -> initImport(versionId, executorService));

        LOGGER.info("Init import task :: watcher");
        executorService.scheduleAtFixedRate(() -> watchVersionStatus(versionId, executorService, importTask), 15, watchDelay, TimeUnit.SECONDS);

        LOGGER.infof("Waiting for all tasks for Version %s to be completed", versionId);
        try {
            boolean terminated = executorService.awaitTermination(6, TimeUnit.HOURS);
            if (terminated) {
                LOGGER.infof("Executor service for Version %s gracefully terminated", versionId);
            } else {
                LOGGER.errorf("Executor service for Version %s could not terminate in the expected time", versionId);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void watchVersionStatus(Long versionId, ExecutorService executorService, Future<?> importTask) {
        boolean isVersionBeingCancelled = QuarkusTransaction.call(
                QuarkusTransaction.runOptions()
                        .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                        .semantic(RunOptions.Semantic.DISALLOW_EXISTING),
                () -> {
                    VersionEntity watchedVersion = VersionEntity.findById(versionId);
                    return watchedVersion.status.equals(Status.CANCELLING);
                }
        );

        if (isVersionBeingCancelled) {
            LOGGER.infof("Found Version %s in CANCELLING state. Shutting down import task", versionId);
            importTask.cancel(true);

            QuarkusTransaction.run(
                    QuarkusTransaction.runOptions()
                            .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                            .semantic(RunOptions.Semantic.DISALLOW_EXISTING),
                    () -> {
                        VersionEntity watchedVersion = VersionEntity.findById(versionId);
                        watchedVersion.status = Status.CANCELLED;
                        watchedVersion.persist();
                    }
            );

            LOGGER.infof("Shutting down all import tasks for Version %s", versionId);
            shutdownExecutorService(executorService);
        }
    }

    private void initImport(Long versionId, ScheduledExecutorService executorService) {
        File zipFile;
        File unzippedFolder;
        File txtFile;

        try {
            // Download file
            downloadingVersionEvent.fire(() -> versionId);
            zipFile = fileService.downloadFile();

            // Unzip file
            unzippingVersionEvent.fire(() -> versionId);
            unzippedFolder = fileService.unzip(zipFile);

            // Find .txt file
            txtFile = fileService.getFirstTxtFileFound(unzippedFolder.listFiles());
        } catch (IOException e) {
            LOGGER.error(e);
            return;
        }

        // Create Partition
        executeUpdate(String.format(CREATE_PARTITION_SQL, versionId, versionId + 1));

        // Persist data
        int totalRowsImported;
        try {
            importingVersionEvent.fire(() -> versionId);
            totalRowsImported = processTxtFile(versionId, txtFile, executorService);
        } catch (IOException e) {
            LOGGER.error(e);
            return;
        }

        // Clear folders
        try {
            LOGGER.infof("Deleting directory %s", zipFile.toString());
            LOGGER.infof("Deleting directory %s", unzippedFolder.toString());
            zipFile.delete();
            txtFile.delete();
            FileUtils.deleteDirectory(unzippedFolder);
        } catch (IOException e) {
            LOGGER.error(e);
        }

        // Save final state of version
        QuarkusTransaction.run(
                QuarkusTransaction.runOptions()
                        .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                        .semantic(RunOptions.Semantic.DISALLOW_EXISTING),
                () -> {
                    VersionEntity version = VersionEntity.findById(versionId);
                    version.status = Status.INDEXING;
                    version.updatedAt = new Date();
                    version.records = totalRowsImported;
                    version.persist();
                }
        );

        // Create indexes
        LOGGER.infof("Indexing version %s", versionId);

        executeUpdate(String.format(CREATE_INDEX_SQL, versionId));
        boolean waitIndexProcessing = true;
        do {
            ScheduledFuture<Long> schedule = executorService.schedule(() -> {
                try (
                        Connection connection = dataSource.getConnection();
                        Statement statement = connection.createStatement()
                ) {
                    ResultSet resultSet = statement.executeQuery(SELECT_INDEX_WATCH_SQL);
                    long numberOfIndexesInProgress = 0;
                    while (resultSet.next()) {
                        numberOfIndexesInProgress = resultSet.getLong(1);
                    }
                    resultSet.close();
                    return numberOfIndexesInProgress;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }, watchDelay, TimeUnit.SECONDS);

            try {
                waitIndexProcessing = schedule.get() > 0;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        } while (waitIndexProcessing);

        // Save final state of version
        QuarkusTransaction.run(
                QuarkusTransaction.runOptions()
                        .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                        .semantic(RunOptions.Semantic.DISALLOW_EXISTING),
                () -> {
                    VersionEntity version = VersionEntity.findById(versionId);
                    version.status = Status.COMPLETED;
                    version.persist();
                }
        );

        // Finish
        LOGGER.infof("Import task finished successfully. Shutting down all import tasks for Version %s", versionId);
        shutdownExecutorService(executorService);
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        executorService.shutdownNow();
    }

    private int processTxtFile(Long versionId, File txtFile, ExecutorService executorService) throws IOException {
        int totalCount = 0;
        int batchCount = 0;

        List<Future<?>> futures = new ArrayList<>();

        Path csvPath = txtFile.getParentFile().toPath().resolve(UUID.randomUUID() + ".csv");
        File csvFile = csvPath.toFile();
        csvFile.createNewFile();
        FileWriter csvFileWriter = new FileWriter(csvFile, StandardCharsets.ISO_8859_1);
        csvFileWriter.write(CSV_HEADER);

        String row;

        try (
                FileReader fileReader = new FileReader(txtFile, StandardCharsets.ISO_8859_1);
                BufferedReader br = new BufferedReader(fileReader)
        ) {
            String line;
            boolean skip = true;
            int batchSize = chunkSize;

            while ((line = br.readLine()) != null) {
                if (skip) {
                    skip = false;
                    continue;
                }

                String[] columns = DataHelper.readLine(line, 15);

                Optional<ContribuyenteEntity> contribuyenteOptional = DataHelper.buildContribuyenteEntity(versionId, columns);
                if (contribuyenteOptional.isEmpty()) {
                    continue;
                }
                ContribuyenteEntity contribuyente = contribuyenteOptional.get();
                Optional<EstadoContribuyente> estadoContribuyente = EstadoContribuyente.fromString(contribuyente.estado);
                if (sunatFilter.isPresent()) {
                    boolean shouldBeSaved = estadoContribuyente.isPresent() && sunatFilter.get().contains(estadoContribuyente.get());
                    if (!shouldBeSaved && contribuyente.getDni() == null) {
                        continue;
                    }
                }

                row = new StringBuilder()
                        .append("\"").append(contribuyente.getId().getVersionId()).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getId().getRuc(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getDni(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getNombre(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getEstado(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getCondicionDomicilio(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getUbigeo(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getTipoVia(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getNombreVia(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getCodigoZona(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getTipoZona(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getNumero(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getInterior(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getLote(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getDepartamento(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getManzana(), "")).append("\"").append(",")
                        .append("\"").append(Objects.toString(contribuyente.getKilometro(), "")).append("\"")
                        .append(System.lineSeparator())
                        .toString();
                csvFileWriter.write(row);

                totalCount = totalCount + 1;
                batchCount = batchCount + 1;

                // Time to save data
                if (batchCount >= batchSize) {
                    csvFileWriter.close();
                    File csvFileToImport = csvFile;
                    Future<?> future = executorService.submit(() -> importCSVFile(versionId, csvFileToImport));
                    futures.add(future);

                    // Reset
                    batchCount = 0;

                    // Create new chunk file
                    csvPath = txtFile.getParentFile().toPath().resolve(UUID.randomUUID() + ".csv");
                    csvFile = csvPath.toFile();
                    csvFile.createNewFile();
                    csvFileWriter = new FileWriter(csvFile, StandardCharsets.ISO_8859_1);
                    csvFileWriter.write(CSV_HEADER);
                }
            }
        }

        csvFileWriter.close();
        File fileToImport = csvFile;
        Future<?> future = executorService.submit(() -> importCSVFile(versionId, fileToImport));
        futures.add(future);

        try {
            for (Future f : futures) {
                f.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error(e);
            throw new RuntimeException(e);
        }

        return totalCount;
    }

    private void importCSVFile(Long versionId, File csvFile) {
        Long rowsInserted;
        try (
                FileReader fileReader = new FileReader(csvFile);
                BufferedReader bufferedReader = new BufferedReader(fileReader);

                Connection connection = dataSource.getConnection()
        ) {
            BaseConnection baseConnection = connection.unwrap(BaseConnection.class);
            CopyManager copyManager = new CopyManager(baseConnection);

            String sql = "COPY contribuyente (" + TABLE_COLUMNS + ") FROM STDIN (FORMAT csv, HEADER true, DELIMITER ',', FORCE_NULL (" + TABLE_COLUMNS + "))";
            rowsInserted = copyManager.copyIn(sql, bufferedReader);

            QuarkusTransaction.run(QuarkusTransaction.runOptions()
                    .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                    .semantic(RunOptions.Semantic.DISALLOW_EXISTING), () -> {
                VersionEntity version = VersionEntity.findById(versionId);
                version.records = version.records + rowsInserted.intValue();
                version.updatedAt = new Date();
                version.persist();
            });
        } catch (IOException e) {
            LOGGER.error(e);
            throw new RuntimeException(e);
        } catch (Throwable e) {
            LOGGER.error(e);
            throw new IllegalStateException(e);
        }

        csvFile.delete();
    }

    private void executeUpdate(String query) {
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.executeUpdate(query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
