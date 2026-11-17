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
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import jakarta.transaction.Transactional;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Date;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@ApplicationScoped
public class VersionService {

    private static final Logger LOGGER = Logger.getLogger(VersionService.class);

    private static final String CREATE_PARTITION_SQL = "create table " + ContribuyenteEntity.VERSION_PARTITION_TABLE + " partition of CONTRIBUYENTE for values from (%1$s) TO (%2$s)";
    private static final String DELETE_PARTITION_SQL = "drop table if exists " + ContribuyenteEntity.VERSION_PARTITION_TABLE;
    /**
     * Built on the version's own partition. Building it on the partitioned parent table would also scan every other
     * partition, and leave an empty index on each of them.
     */
    private static final String CREATE_RUC_INDEX_SQL = "create index ix_contribuyente_version_%1$s_ruc on " + ContribuyenteEntity.VERSION_PARTITION_TABLE + " using hash (ruc)";
    /**
     * Serves the advanced search in {@link io.github.project.openubl.searchpe.models.jpa.ContribuyenteRepository},
     * {@code lower(nombre) like '%text%'}, whose leading wildcard can't use a b-tree. The expression must stay identical
     * to the query's. Needs pg_trgm (V4.3.0).
     */
    private static final String CREATE_NOMBRE_INDEX_SQL = "create index ix_contribuyente_version_%1$s_nombre on " + ContribuyenteEntity.VERSION_PARTITION_TABLE + " using gin (lower(nombre) gin_trgm_ops)";
    private static final String ANALYZE_PARTITION_SQL = "analyze " + ContribuyenteEntity.VERSION_PARTITION_TABLE;
    /**
     * Versions imported before indexes were built on the partition have their index on the parent table, which doesn't
     * go away with the partition. Until it is dropped, new partitions also inherit an empty copy of it.
     */
    private static final String DELETE_LEGACY_INDEX_SQL = "drop index if exists ix_contribuyente_ruc_version_%1$s";

    /**
     * Longest time an import may run. Past it, the import is abandoned and the cleanup job considers the version stale.
     */
    public static final Duration IMPORT_TIMEOUT = Duration.ofHours(6);

    /**
     * Statuses a version goes through while it is being imported.
     */
    private static final Set<Status> IMPORT_STATUSES = EnumSet.of(
            Status.SCHEDULED, Status.DOWNLOADING, Status.UNZIPPING, Status.IMPORTING, Status.INDEXING
    );

    @ConfigProperty(name = "searchpe.sunat.watchDelay")
    int watchDelay;

    @ConfigProperty(name = "searchpe.sunat.minRecordsRatio")
    double minRecordsRatio;

    @ConfigProperty(name = "searchpe.allow.advancedSearch")
    boolean allowAdvancedSearch;

    @Inject
    DataSource dataSource;

    @Inject
    FileService fileService;

    @Inject
    PadronImporter padronImporter;

    @Inject
    VersionRepository versionRepository;

    @Inject
    Event<VersionEvent.DownloadingEvent> downloadingVersionEvent;

    @Inject
    Event<VersionEvent.ImportingDataEvent> importingVersionEvent;

    @Inject
    Event<VersionEvent.ActiveVersionChangedEvent> activeVersionChangedEvent;

    @Transactional
    public void deleteVersion(Long versionId) {
        String deletePartitionQuery = String.format(DELETE_PARTITION_SQL, versionId);
        String deleteLegacyIndexQuery = String.format(DELETE_LEGACY_INDEX_SQL, versionId);
        VersionEntity.getEntityManager()
                .createNativeQuery(deletePartitionQuery)
                .executeUpdate();
        VersionEntity.getEntityManager()
                .createNativeQuery(deleteLegacyIndexQuery)
                .executeUpdate();

        ContribuyenteEntity.delete("id.versionId", versionId);
        VersionEntity.delete("id", versionId);
        activeVersionChangedEvent.fire(() -> versionId);
    }

    @Transactional(Transactional.TxType.NEVER)
    public void importPadronReducidoIntoVersion(Long versionId) {
        // The import task and the status watcher, plus the threads the importer copies chunks with
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2 + PadronImporter.REQUIRED_THREADS);

        LOGGER.info("Init import task");
        Future<?> importTask = executorService.submit(() -> initImport(versionId, executorService));

        LOGGER.info("Init import task :: watcher");
        executorService.scheduleAtFixedRate(() -> watchVersionStatus(versionId, executorService, importTask), 15, watchDelay, TimeUnit.SECONDS);

        LOGGER.infof("Waiting for all tasks for Version %s to be completed", versionId);
        try {
            boolean terminated = executorService.awaitTermination(IMPORT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (terminated) {
                LOGGER.infof("Executor service for Version %s gracefully terminated", versionId);
            } else {
                LOGGER.errorf("Executor service for Version %s could not terminate in the expected time", versionId);
                shutdownExecutorService(executorService);
                markAsFailed(versionId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void watchVersionStatus(Long versionId, ExecutorService executorService, Future<?> importTask) {
        boolean isVersionBeingCancelled = QuarkusTransaction.requiringNew().call(() -> {
            VersionEntity watchedVersion = VersionEntity.findById(versionId);
            return watchedVersion.status.equals(Status.CANCELLING);
        });

        if (isVersionBeingCancelled) {
            LOGGER.infof("Found Version %s in CANCELLING state. Shutting down import task", versionId);
            importTask.cancel(true);
            markAsCancelled(versionId);

            LOGGER.infof("Shutting down all import tasks for Version %s", versionId);
            shutdownExecutorService(executorService);
        }
    }

    /**
     * Runs the import and guarantees that, whatever happens, the version doesn't stay in an in-progress status and the
     * executor (including the status watcher) is shut down.
     */
    private void initImport(Long versionId, ExecutorService executorService) {
        try {
            runImport(versionId, executorService);
            LOGGER.infof("Import of Version %s finished successfully", versionId);
        } catch (Exception e) {
            if (e instanceof CancellationException || e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                LOGGER.infof("Import of Version %s was cancelled", versionId);
            } else if (e instanceof IncompletePadronException) {
                LOGGER.error(e.getMessage());
            } else {
                LOGGER.errorf(e, "Import of Version %s failed", versionId);
            }
            // Stop the chunk imports that are still running before recording the final status
            shutdownExecutorService(executorService);
            markAsFailed(versionId);
        } finally {
            shutdownExecutorService(executorService);
        }
    }

    private void runImport(Long versionId, ExecutorService executorService) throws IOException, InterruptedException {
        File zipFile = null;
        try {
            downloadingVersionEvent.fire(() -> versionId);
            zipFile = fileService.downloadFile();

            executeUpdate(String.format(CREATE_PARTITION_SQL, versionId, versionId + 1));

            importingVersionEvent.fire(() -> versionId);
            int totalRowsImported = padronImporter.importPadron(versionId, zipFile, executorService);
            checkRecordCount(versionId, totalRowsImported);
            updateVersion(versionId, version -> {
                version.status = Status.INDEXING;
                version.records = totalRowsImported;
            });
        } finally {
            deleteDownloadedFile(zipFile);
        }

        preparePartitionForQueries(versionId);
        updateVersion(versionId, version -> version.status = Status.COMPLETED);
        // No transaction here: updateVersion already committed, so the AFTER_SUCCESS observer runs immediately
        activeVersionChangedEvent.fire(() -> versionId);
    }

    /**
     * Refuses to promote a version that is much smaller than the active one, which usually means SUNAT published a
     * truncated or broken file. The import then fails and the active version stays in place. An import with no rows is
     * always rejected, even when there is no active version.
     */
    private void checkRecordCount(Long versionId, int totalRowsImported) {
        Optional<VersionEntity> activeVersion = versionRepository.findActive();
        int minimumRecords = minimumRecords(activeVersion.map(version -> version.records).orElse(0), minRecordsRatio);
        if (totalRowsImported < minimumRecords) {
            throw new IncompletePadronException(String.format(
                    "Version %s imported %d records, fewer than the %d required by searchpe.sunat.minRecordsRatio=%s "
                            + "(active version: %s)",
                    versionId, totalRowsImported, minimumRecords, minRecordsRatio,
                    activeVersion.map(version -> version.id + " with " + version.records + " records").orElse("none")
            ));
        }
    }

    /**
     * @param activeVersionRecords records of the active version, or 0 if there is none
     * @return the minimum number of records a new version needs to replace the active one, never less than 1
     */
    static int minimumRecords(int activeVersionRecords, double minRecordsRatio) {
        return Math.max(1, (int) Math.ceil(activeVersionRecords * minRecordsRatio));
    }

    void validateConfig(@Observes StartupEvent event) {
        if (minRecordsRatio < 0 || minRecordsRatio > 1) {
            throw new IllegalArgumentException(
                    "searchpe.sunat.minRecordsRatio must be between 0 and 1, but it is " + minRecordsRatio
            );
        }
    }

    /**
     * Indexes the version's partition and refreshes its planner statistics. CREATE INDEX is synchronous, so the
     * partition is ready to query when this returns.
     */
    private void preparePartitionForQueries(Long versionId) {
        LOGGER.infof("Indexing and analyzing the partition of version %s", versionId);
        executeUpdate(String.format(CREATE_RUC_INDEX_SQL, versionId));
        // Only worth its build time when the advanced search is enabled. Enabling it later takes effect from the next
        // import.
        if (allowAdvancedSearch) {
            LOGGER.infof("Building the advanced search index of version %s", versionId);
            executeUpdate(String.format(CREATE_NOMBRE_INDEX_SQL, versionId));
        }
        executeUpdate(String.format(ANALYZE_PARTITION_SQL, versionId));
    }

    /**
     * Loads the version in a new transaction, applies {@code changes} and refreshes {@code updatedAt}.
     *
     * @throws CancellationException if the version is being cancelled, so the import stops instead of overwriting the
     *                               user's cancellation
     */
    private void updateVersion(Long versionId, Consumer<VersionEntity> changes) {
        QuarkusTransaction.requiringNew().run(() -> {
            VersionEntity version = VersionEntity.findById(versionId);
            if (version.status == Status.CANCELLING || version.status == Status.CANCELLED) {
                throw new CancellationException("Version " + versionId + " is being cancelled");
            }
            changes.accept(version);
            version.updatedAt = new Date();
            version.persist();
        });
    }

    private void markAsCancelled(Long versionId) {
        updateStatus(versionId, Status.CANCELLING, Status.CANCELLED);
    }

    /**
     * Records that the import ended without completing. A pending cancellation wins: CANCELLING becomes CANCELLED.
     * Otherwise the import status becomes ERROR.
     */
    private void markAsFailed(Long versionId) {
        markAsCancelled(versionId);
        for (Status importStatus : IMPORT_STATUSES) {
            updateStatus(versionId, importStatus, Status.ERROR);
        }
    }

    /**
     * Moves the version to {@code to} only if it is still in {@code from}, in a single statement so it can't race with
     * the REST API or the status watcher. It bumps the {@code @Version} column, so a concurrent write of a stale entity
     * fails instead of silently overwriting the new status.
     */
    private void updateStatus(Long versionId, Status from, Status to) {
        QuarkusTransaction.requiringNew().run(() -> VersionEntity.update(
                "status = ?1, updatedAt = ?2, version = version + 1 where id = ?3 and status = ?4",
                to, new Date(), versionId, from
        ));
    }

    private void deleteDownloadedFile(File zipFile) {
        if (zipFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(zipFile.toPath());
        } catch (IOException e) {
            LOGGER.warn("Could not delete the downloaded padrón " + zipFile, e);
        }
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        executorService.shutdownNow();
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
