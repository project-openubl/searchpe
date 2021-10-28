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
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.event.Observes;
import javax.inject.Singleton;
import javax.transaction.Transactional;

@Singleton
public class SearchpeBootstrap {

    @Transactional
    void createAdminUserOnStart(@Observes StartupEvent ev) {
        if (BasicUserEntity.count() == 0) {
            BasicUserEntity.add("admin", "admin", RoleType.admin.toString());
        }
    }

}
