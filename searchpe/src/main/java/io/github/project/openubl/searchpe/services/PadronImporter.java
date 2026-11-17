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

import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.EstadoContribuyente;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.utils.DataHelper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * Streams the padrón straight from the downloaded zip into a version's partition. Lines are parsed into CSV chunks of
 * {@code searchpe.sunat.chunkSize} rows held in memory, and each chunk is loaded with COPY on the given executor. At
 * most {@link #MAX_CHUNKS_IN_FLIGHT} chunks wait or run at a time, so memory stays bounded however big the padrón is.
 */
@ApplicationScoped
public class PadronImporter {

    /**
     * Chunks being copied, or waiting to be, at the same time.
     */
    static final int MAX_CHUNKS_IN_FLIGHT = 3;

    /**
     * Threads the executor passed to {@link #importPadron} needs to copy chunks at full speed.
     */
    public static final int REQUIRED_THREADS = MAX_CHUNKS_IN_FLIGHT;

    private static final int PADRON_COLUMNS = 15;

    private static final String TABLE_COLUMNS = String.join(",",
            "version_id", "ruc", "dni", "nombre", "estado", "condicion_domicilio", "ubigeo", "tipo_via", "nombre_via",
            "codigo_zona", "tipo_zona", "numero", "interior", "lote", "departamento", "manzana", "kilometro"
    );

    /**
     * Copies into the version's partition rather than the parent table, which saves routing every row. FORCE_NULL turns
     * the empty quoted values written for missing columns into NULL.
     */
    private static final String COPY_SQL = "COPY " + ContribuyenteEntity.VERSION_PARTITION_TABLE + " (" + TABLE_COLUMNS + ") FROM STDIN "
            + "(FORMAT csv, FORCE_NULL (" + TABLE_COLUMNS + "))";

    @ConfigProperty(name = "searchpe.sunat.filter")
    Optional<List<EstadoContribuyente>> sunatFilter;

    @ConfigProperty(name = "searchpe.sunat.chunkSize")
    int chunkSize;

    @Inject
    DataSource dataSource;

    @Inject
    FileService fileService;

    /**
     * Stops at the first chunk that fails, and cancels the chunks still in flight whenever it doesn't complete.
     *
     * @param executorService runs the COPY of each chunk, with {@link #REQUIRED_THREADS} threads
     * @return the number of rows imported
     */
    public int importPadron(Long versionId, File zipFile, ExecutorService executorService) throws IOException, InterruptedException {
        List<Future<?>> chunkImports = new ArrayList<>();
        Semaphore chunksInFlight = new Semaphore(MAX_CHUNKS_IN_FLIGHT);
        boolean completed = false;

        try (BufferedReader padron = fileService.openPadron(zipFile)) {
            padron.readLine(); // Header

            StringBuilder chunk = new StringBuilder();
            int rowsInChunk = 0;
            int totalRows = 0;
            String line;
            while ((line = padron.readLine()) != null) {
                Optional<ContribuyenteEntity> contribuyente = toContribuyente(versionId, line);
                if (contribuyente.isEmpty()) {
                    continue;
                }

                appendCsvRow(chunk, contribuyente.get());
                totalRows++;
                if (++rowsInChunk >= chunkSize) {
                    chunkImports.add(submitChunk(versionId, chunk, executorService, chunksInFlight));
                    chunk.setLength(0);
                    rowsInChunk = 0;
                    removeFinished(chunkImports);
                }
            }
            if (rowsInChunk > 0) {
                chunkImports.add(submitChunk(versionId, chunk, executorService, chunksInFlight));
            }

            for (Future<?> chunkImport : chunkImports) {
                getResult(chunkImport);
            }
            completed = true;
            return totalRows;
        } finally {
            if (!completed) {
                chunkImports.forEach(chunkImport -> chunkImport.cancel(true));
            }
        }
    }

    void validateConfig(@Observes StartupEvent event) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("searchpe.sunat.chunkSize must be positive, but it is " + chunkSize);
        }
    }

    private Optional<ContribuyenteEntity> toContribuyente(Long versionId, String line) {
        String[] columns = DataHelper.readLine(line, PADRON_COLUMNS);
        return DataHelper.buildContribuyenteEntity(versionId, columns).filter(this::shouldBeImported);
    }

    /**
     * Natural persons are always imported, so they can be looked up by DNI. Other taxpayers are imported only if their
     * status passes {@code searchpe.sunat.filter}.
     */
    private boolean shouldBeImported(ContribuyenteEntity contribuyente) {
        if (sunatFilter.isEmpty() || contribuyente.getDni() != null) {
            return true;
        }
        return EstadoContribuyente.fromString(contribuyente.getEstado())
                .map(sunatFilter.get()::contains)
                .orElse(false);
    }

    /**
     * Every value is quoted, so commas inside values are safe; {@link DataHelper#readLine} already removed double
     * quotes. Missing values are written as empty quoted values, which COPY turns into NULL.
     */
    private static void appendCsvRow(StringBuilder csv, ContribuyenteEntity contribuyente) {
        String[] values = {
                String.valueOf(contribuyente.getId().getVersionId()),
                contribuyente.getId().getRuc(),
                contribuyente.getDni(),
                contribuyente.getNombre(),
                contribuyente.getEstado(),
                contribuyente.getCondicionDomicilio(),
                contribuyente.getUbigeo(),
                contribuyente.getTipoVia(),
                contribuyente.getNombreVia(),
                contribuyente.getCodigoZona(),
                contribuyente.getTipoZona(),
                contribuyente.getNumero(),
                contribuyente.getInterior(),
                contribuyente.getLote(),
                contribuyente.getDepartamento(),
                contribuyente.getManzana(),
                contribuyente.getKilometro()
        };
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append('"').append(values[i] != null ? values[i] : "").append('"');
        }
        csv.append('\n');
    }

    /**
     * Blocks while {@link #MAX_CHUNKS_IN_FLIGHT} chunks are in flight, so parsing can't get ahead of the database and
     * pile chunks up in memory. The chunk is copied to a String only once a slot is free.
     */
    private Future<?> submitChunk(Long versionId, CharSequence chunk, ExecutorService executorService, Semaphore chunksInFlight)
            throws InterruptedException {
        chunksInFlight.acquire();
        String csv = chunk.toString();
        try {
            return executorService.submit(() -> {
                try {
                    copyChunk(versionId, csv);
                } finally {
                    chunksInFlight.release();
                }
            });
        } catch (RejectedExecutionException e) {
            chunksInFlight.release();
            throw e;
        }
    }

    private void copyChunk(Long versionId, String csv) {
        try (Connection connection = dataSource.getConnection()) {
            CopyManager copyManager = new CopyManager(connection.unwrap(BaseConnection.class));
            long rowsInserted = copyManager.copyIn(String.format(COPY_SQL, versionId), new StringReader(csv));

            // Bulk update, so concurrent chunks don't fail each other on the entity's optimistic lock. It only
            // reports progress: the final count is set once all chunks are imported.
            QuarkusTransaction.requiringNew().run(() -> VersionEntity.update(
                    "records = records + ?1, updatedAt = ?2 where id = ?3",
                    (int) rowsInserted, new Date(), versionId
            ));
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Could not copy a chunk of Version " + versionId, e);
        }
    }

    /**
     * Forgets the chunks already copied, and fails right away if one of them failed instead of parsing the rest of the
     * padrón into the same error.
     */
    private static void removeFinished(List<Future<?>> chunkImports) throws InterruptedException {
        Iterator<Future<?>> iterator = chunkImports.iterator();
        while (iterator.hasNext()) {
            Future<?> chunkImport = iterator.next();
            if (chunkImport.isDone()) {
                getResult(chunkImport);
                iterator.remove();
            }
        }
    }

    private static void getResult(Future<?> chunkImport) throws InterruptedException {
        try {
            chunkImport.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Could not import a chunk of the padrón", e.getCause());
        }
    }
}
