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
package io.github.project.openubl.searchpe.models.jpa;

import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.cache.CacheResult;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Transactional
@ApplicationScoped
public class VersionRepository implements PanacheRepository<VersionEntity> {

    public static final String ACTIVE_VERSION_ID_CACHE = "active-version-id";

    /**
     * Uncached. Use this rather than {@link #findActiveId()} for decisions that must not act on a stale value, such as
     * choosing which versions to delete.
     */
    public Optional<VersionEntity> findActive() {
        Sort sort = Sort.by("createdAt").descending();
        return VersionEntity.find(
                "status = :status",
                sort,
                Parameters.with("status", Status.COMPLETED)
        ).firstResultOptional();
    }

    /**
     * Cached id of the active version, which every lookup needs. The cache is invalidated when the active version
     * changes on this instance (see {@link ActiveVersionCacheInvalidator}), and expires after a few seconds so other
     * replicas pick up the change too.
     */
    @CacheResult(cacheName = ACTIVE_VERSION_ID_CACHE)
    public Optional<Long> findActiveId() {
        return findActive().map(version -> version.id);
    }

    /**
     * Lists the versions that can be deleted: every finished version except the active one, plus versions that are
     * still in progress but haven't been updated since {@code staleBefore} (their import or deletion died, e.g.
     * because the pod was restarted).
     */
    public List<VersionEntity> listObsolete(Date staleBefore) {
        Optional<VersionEntity> activeVersion = findActive();
        return listAll().stream()
                .filter(version -> activeVersion.map(active -> !active.equals(version)).orElse(true))
                .filter(version -> version.status.isFinished() || version.updatedAt.before(staleBefore))
                .toList();
    }
}
