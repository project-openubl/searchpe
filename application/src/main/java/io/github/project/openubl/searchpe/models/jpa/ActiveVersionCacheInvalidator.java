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

import io.github.project.openubl.searchpe.models.VersionEvent;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

/**
 * Invalidates {@link VersionRepository#findActiveId()} once the change that fired the event is committed (or right
 * away, if it was fired outside a transaction), so a request running in between can't cache the old value again.
 */
@ApplicationScoped
public class ActiveVersionCacheInvalidator {

    @CacheInvalidateAll(cacheName = VersionRepository.ACTIVE_VERSION_ID_CACHE)
    void onActiveVersionChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) VersionEvent.ActiveVersionChangedEvent event) {
        // The interceptor does the work
    }
}
