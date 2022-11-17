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
package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.dto.VersionDto;
import io.github.project.openubl.searchpe.jobs.clean.DeleteVersionsScheduler;
import io.github.project.openubl.searchpe.jobs.ingest.IngestDataScheduler;
import io.github.project.openubl.searchpe.mapper.VersionMapper;
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.security.Permission;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.RunOptions;
import io.quarkus.panache.common.Sort;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.logging.Logger;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transactional;
import javax.transaction.UserTransaction;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Transactional
@ApplicationScoped
@Path("/versions")
@Produces("application/json")
public class VersionResource {

    private static final Logger logger = Logger.getLogger(VersionResource.class);

    @Inject
    Scheduler quartz;

    @Inject
    UserTransaction tx;

    @Inject
    IngestDataScheduler ingestDataScheduler;

    @Inject
    DeleteVersionsScheduler deleteVersionsScheduler;

    @Inject
    VersionRepository versionRepository;

    @Inject
    VersionMapper versionMapper;

    @RolesAllowed({Permission.admin, Permission.version_write})
    @Operation(summary = "Get versions", description = "Get all versions available")
    @GET
    @Path("/")
    @Consumes("application/json")
    public List<VersionDto> getVersions(@QueryParam("active") Boolean active) {
        if (active != null) {
            List<VersionEntity> activeList = versionRepository.findActive().map(Arrays::asList).orElse(Collections.emptyList());
            if (active) {
                return activeList.stream()
                        .map(entity -> versionMapper.toDto(entity))
                        .collect(Collectors.toList());
            } else {
                Sort sort = Sort.by("id").descending();
                List<VersionEntity> allList = VersionEntity.findAll(sort).list();

                allList.removeAll(activeList);
                return allList.stream()
                        .map(entity -> versionMapper.toDto(entity))
                        .collect(Collectors.toList());
            }
        }

        Sort sort = Sort.by("id").descending();
        return VersionEntity.findAll(sort).<VersionEntity>list().stream()
                .map(entity -> versionMapper.toDto(entity))
                .collect(Collectors.toList());
    }

    @RolesAllowed({Permission.admin, Permission.version_write})
    @Operation(summary = "Create version", description = "Creates a new version and fires the importing process")
    @Transactional(Transactional.TxType.NEVER)
    @POST
    @Path("/")
    @Consumes("application/json")
    public VersionDto createVersion() {
        try {
            tx.begin();
            VersionEntity version = VersionEntity.generateNew();
            version.persistAndFlush();
            tx.commit();

            try {
                ingestDataScheduler.scheduleProgrammatically(version.id);
            } catch (SchedulerException e) {
                logger.error("Could not schedule import of data into VersionEntity", e);

                tx.begin();
                VersionEntity scheduledVersion = VersionEntity.findById(version.id);
                scheduledVersion.status = Status.ERROR;
                scheduledVersion.persist();
                tx.commit();
            }

            return versionMapper.toDto(version);
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException |
                 SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                throw new IllegalStateException(se);
            }
            throw new InternalServerErrorException(e);
        }
    }

    @RolesAllowed({Permission.admin, Permission.version_write})
    @Operation(summary = "Get version", description = "Get version by id")
    @GET
    @Path("/{id}")
    @Consumes("application/json")
    public VersionDto getVersion(@PathParam("id") Long id) {
        VersionEntity version = VersionEntity.findById(id);
        if (version == null) {
            throw new NotFoundException("Version[id=" + id + "] does not exists");
        }

        return versionMapper.toDto(version);
    }

    @RolesAllowed({Permission.admin, Permission.version_write})
    @Operation(summary = "Delete version", description = "Delete version by id")
    @Transactional(Transactional.TxType.NEVER)
    @DELETE
    @Path("/{id}")
    @Consumes("application/json")
    public void deleteVersion(@PathParam("id") Long id) {
        try {
            tx.begin();

            VersionEntity version = VersionEntity.findById(id);
            if (version == null) {
                throw new NotFoundException("Version[id=" + id + "] does not exists");
            }

            Status prevStatus = version.status;
            version.status = Status.DELETING;
            version.persist();

            tx.commit();


            try {
                deleteVersionsScheduler.scheduleProgrammatically(version.id);
            } catch (SchedulerException e) {
                logger.error("Could not schedule delete of VersionEntity:" + version.id, e);

                tx.begin();
                VersionEntity scheduledVersion = VersionEntity.findById(version.id);
                scheduledVersion.status = prevStatus;
                scheduledVersion.persist();
                tx.commit();
            }
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException |
                 SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                throw new IllegalStateException(se);
            }
            throw new InternalServerErrorException(e);
        }
    }

    @RolesAllowed({Permission.admin, Permission.version_write})
    @Operation(summary = "Update version", description = "Update version status")
    @Transactional(Transactional.TxType.NEVER)
    @PUT
    @Path("/{id}")
    @Consumes("application/json")
    public void updateVersion(@PathParam("id") Long id, VersionEntity dto) {
        if (!dto.status.equals(Status.CANCELLING)) {
            throw new BadRequestException();
        }

        QuarkusTransaction.run(QuarkusTransaction.runOptions()
                .exceptionHandler((throwable) -> RunOptions.ExceptionResult.ROLLBACK)
                .semantic(RunOptions.Semantic.DISALLOW_EXISTING), () -> {

            VersionEntity version = VersionEntity.findById(id);
            if (version == null) {
                throw new NotFoundException();
            }

            try {
                String[] versionTriggeredKey = version.triggerKey.split("\\.");
                TriggerKey triggerKey = TriggerKey.triggerKey(versionTriggeredKey[0], versionTriggeredKey[1]);

                Optional<JobExecutionContext> currentExecutingJob = quartz.getCurrentlyExecutingJobs().stream()
                        .filter(jobExecutionContext -> jobExecutionContext.getTrigger().getKey().equals(triggerKey))
                        .findFirst();
                if (currentExecutingJob.isPresent()) {
                    version.status = Status.CANCELLING;
                } else {
                    version.status = Status.CANCELLED;
                }
            } catch (SchedulerException e) {
                throw new RuntimeException(e);
            }

            version.persist();
        });
    }
}
