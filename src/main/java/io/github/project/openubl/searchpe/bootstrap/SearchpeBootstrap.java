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
package io.github.project.openubl.searchpe.bootstrap;

import io.github.project.openubl.searchpe.models.RoleType;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.runtime.StartupEvent;
import org.hibernate.search.mapper.orm.session.SearchSession;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.transaction.Transactional;

@Singleton
public class SearchpeBootstrap {

    @Inject
    SearchSession searchSession;

    @Transactional
    void createAdminUserOnStart(@Observes StartupEvent ev) {
        if (BasicUserEntity.count() == 0) {
            BasicUserEntity.add("admin", "admin", RoleType.admin.toString());
        }
    }

    /**
     * If there is an upgrade of Version, the indexes in Elasticsearch should be deleted.
     * This will work under the assumption that on upgrades the DB will delete all data before.
     */
    @Transactional
    void reindexSearchIndexes(@Observes StartupEvent ev) throws InterruptedException {
        if (VersionEntity.count() == 0) {
            searchSession.massIndexer().purgeAllOnStart(true).startAndWait();
        }
    }

}
