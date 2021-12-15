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

import io.github.project.openubl.searchpe.jobs.clean.DeleteVersionProgrammaticallyScheduler;
import io.github.project.openubl.searchpe.jobs.ingest.IngestDataProgrammaticallyScheduler;
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.security.Permission;
import io.quarkus.panache.common.Sort;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.logging.Logger;
import org.quartz.SchedulerException;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.NotSupportedException;
import javax.transaction.*;
import javax.ws.rs.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Transactional
@RequestScoped
@Path("/versions")
@Produces("application/json")
public class VersionResource {

    private static final Logger logger = Logger.getLogger(VersionResource.class);

    @Inject
    UserTransaction tx;

    @Inject
    IngestDataProgrammaticallyScheduler ingestDataProgrammaticallyScheduler;

    @Inject
    DeleteVersionProgrammaticallyScheduler deleteVersionProgrammaticallyScheduler;

    @Inject
    VersionRepository versionRepository;

    @RolesAllowed({Permission.admin, Permission.version_write})
    @Operation(summary = "Get versions", description = "Get all versions available")
    @GET
    @Path("/")
    @Consumes("application/json")
    public List<VersionEntity> getVersions(@QueryParam("active") Boolean active) {
        if (active != null) {
            List<VersionEntity> activeList = versionRepository.findActive().map(Arrays::asList).orElse(Collections.emptyList());
            if (active) {
                return activeList;
            } else {
                Sort sort = Sort.by("id").descending();
                List<VersionEntity> allList = VersionEntity.findAll(sort).list();

                allList.removeAll(activeList);
                return allList;
            }
        }

        Sort sort = Sort.by("id").descending();
        return VersionEntity.findAll(sort).list();
    }

    @RolesAllowed({Permission.admin, Permission.version_write})
    @Operation(summary = "Create version", description = "Creates a new version and fires the importing process")
    @Transactional(Transactional.TxType.NEVER)
    @POST
    @Path("/")
    @Consumes()
    public VersionEntity createVersion() {
        try {
            tx.begin();
            VersionEntity version = VersionEntity.generateNew();
            version.persistAndFlush();
            tx.commit();


            try {
                ingestDataProgrammaticallyScheduler.schedule(version.id);
            } catch (SchedulerException e) {
                logger.error("Could not schedule import of data into VersionEntity", e);

                tx.begin();
                VersionEntity scheduledVersion = VersionEntity.findById(version.id);
                scheduledVersion.status = Status.ERROR;
                scheduledVersion.persist();
                tx.commit();
            }

            return version;
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException | SystemException e) {
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
    public VersionEntity getVersion(@PathParam("id") Long id) {
        VersionEntity version = VersionEntity.findById(id);
        if (version == null) {
            throw new NotFoundException("Version[id=" + id + "] does not exists");
        }

        return version;
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
                deleteVersionProgrammaticallyScheduler.schedule(version.id);
            } catch (SchedulerException e) {
                logger.error("Could not schedule delete of VersionEntity:" + version.id, e);

                tx.begin();
                VersionEntity scheduledVersion = VersionEntity.findById(version.id);
                scheduledVersion.status = prevStatus;
                scheduledVersion.persist();
                tx.commit();
            }
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException | SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                throw new IllegalStateException(se);
            }
            throw new InternalServerErrorException(e);
        }
    }

}
