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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class UpgradeDataService {

    private static final Logger LOGGER = Logger.getLogger(UpgradeDataService.class);

    @ConfigProperty(name = "searchpe.sunat.filter")
    Optional<List<EstadoContribuyente>> sunatFilter;

    @ConfigProperty(name = "searchpe.sunat.chunkSize")
    int chunkSize;

    @Inject
    FileService fileService;

    @Inject
    Event<VersionEvent.DownloadingEvent> downloadingVersionEvent;

    @Inject
    Event<VersionEvent.UnzippingFileEvent> unzippingVersionEvent;

    @Inject
    Event<VersionEvent.ImportingDataEvent> importingVersionEvent;

    public void upgrade(Long versionId) {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);

        LOGGER.info("Init version import task");
        Future<?> importTask = executorService.submit(() -> importTask(versionId, executorService));

        LOGGER.info("Init version import task watcher");
        executorService.scheduleAtFixedRate(() -> {
            boolean isVersionBeingCancelled = QuarkusTransaction.call(
                    QuarkusTransaction.runOptions()
                            .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                            .semantic(RunOptions.Semantic.DISALLOW_EXISTING),
                    () -> {
                        VersionEntity watchedVersion = VersionEntity.findById(versionId);
                        LOGGER.infof("Watch Version %s %s", versionId, watchedVersion.status);
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
        }, 15, 15, TimeUnit.SECONDS);

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

    private void importTask(Long versionId, ExecutorService executorService) {
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
        }

        // Finish
        LOGGER.infof("Import task finished successfully. Shutting down all import tasks for Version %s", versionId);
        shutdownExecutorService(executorService);
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        executorService.shutdownNow();
    }

    private void createContribuyentesFromFile(Long versionId, File file) throws IOException {
        LOGGER.infof("Start importing contribuyentes");
        long startTime = Calendar.getInstance().getTimeInMillis();

        int totalCount = 0;
        int batchCount = 0;

        List<ContribuyenteEntity> contribuyentes = new ArrayList<>();
        try (
                FileReader fileReader = new FileReader(file, StandardCharsets.ISO_8859_1);
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

                contribuyentes.add(contribuyente);

                totalCount = totalCount + 1;
                batchCount = batchCount + 1;

                // Time to save data
                if (batchCount >= batchSize) {
                    saveProgress(versionId, contribuyentes).toCompletableFuture().get();

                    // Reset
                    batchCount = 0;
                    contribuyentes.clear();
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Save remaining data
        try {
            if (!contribuyentes.isEmpty()) {
                saveProgress(versionId, contribuyentes).toCompletableFuture().get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        // Save final state of version
        int records = totalCount;
        QuarkusTransaction.run(QuarkusTransaction.runOptions()
                .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                .semantic(RunOptions.Semantic.DISALLOW_EXISTING), () -> {

            VersionEntity version = VersionEntity.findById(versionId);
            version.status = Status.COMPLETED;
            version.updatedAt = new Date();
            version.records = records;
            version.persist();
        });

        long endTime = Calendar.getInstance().getTimeInMillis();
        LOGGER.infof("Import contribuyentes finished successfully in " + (endTime - startTime) + " milliseconds.");
    }

    private CompletionStage<Void> saveProgress(Long versionId, List<ContribuyenteEntity> contribuyentes) {
        return CompletableFuture.runAsync(() -> {
            QuarkusTransaction.run(QuarkusTransaction.runOptions()
                    .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                    .semantic(RunOptions.Semantic.DISALLOW_EXISTING), () -> {
                VersionEntity version = VersionEntity.findById(versionId);
                version.records = version.records + contribuyentes.size();
                version.updatedAt = new Date();
                version.persist();

                ContribuyenteEntity.persist(contribuyentes);
            });

            LOGGER.debugf("Chunk processed size=" + contribuyentes.size());
        });
    }

}
