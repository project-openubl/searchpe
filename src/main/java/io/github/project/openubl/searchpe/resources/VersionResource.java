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
package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.jobs.DeleteVersionJob;
import io.github.project.openubl.searchpe.jobs.UpgradeDataJob;
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.panache.common.Sort;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.quartz.SchedulerException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Transactional
@ApplicationScoped
@Path("/versions")
@Consumes("application/json")
public class VersionResource {

    @Inject
    UpgradeDataJob upgradeDataJob;

    @Inject
    DeleteVersionJob deleteVersionJob;

    @Inject
    VersionRepository versionRepository;

    @Operation(summary = "Get versions", description = "Get all versions available")
    @GET
    @Path("/")
    @Produces("application/json")
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

    @Operation(summary = "Create version", description = "Creates a new version and fires the importing process")
    @POST
    @Path("/")
    @Produces("application/json")
    public VersionEntity createVersion() {
        Date currentTime = new Date();

        VersionEntity version = VersionEntity.Builder.aVersionEntity()
                .withCreatedAt(currentTime)
                .withUpdatedAt(currentTime)
                .withStatus(Status.SCHEDULED)
                .withRecords(0)
                .build();

        version.persistAndFlush();

        try {
            upgradeDataJob.trigger(version);
        } catch (SchedulerException e) {
            throw new InternalServerErrorException(e);
        }

        return version;
    }

    @Operation(summary = "Get version", description = "Get version by id")
    @GET
    @Path("/{id}")
    @Produces("application/json")
    public VersionEntity getVersion(@PathParam("id") Long id) {
        VersionEntity version = VersionEntity.findById(id);
        if (version == null) {
            throw new NotFoundException("Version[id=" + id + "] does not exists");
        }

        return version;
    }

    @Operation(summary = "Delete version", description = "Delete version by id")
    @DELETE
    @Path("/{id}")
    @Produces("application/json")
    public void deleteVersion(@PathParam("id") Long id) {
        VersionEntity version = VersionEntity.findById(id);
        if (version == null) {
            throw new NotFoundException("Version[id=" + id + "] does not exists");
        }

        version.status = Status.DELETING;
        version.persist();

        try {
            deleteVersionJob.trigger(version);
        } catch (SchedulerException e) {
            throw new InternalServerErrorException(e);
        }
    }
}
