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
package io.github.project.openubl.searchpe.jobs;

public interface SearchpeJobs {

    // Jobs
    String VERSION_ENTITY_JOB_GROUP = "versionEntityJobGroup";

    String VERSION_ENTITY_IMPORT_JOB = "versionEntityImportJob";
    String VERSION_ENTITY_CLEAN_JOB = "versionEntityCleanJob";
    String VERSION_ENTITY_DELETE_JOB = "versionEntityDeleteJob";


    // Triggers
    String VERSION_ENTITY_TRIGGER_GROUP = "versionEntityTriggerGroup";

    String VERSION_ENTITY_TRIGGER_IMPORT_CRON = "versionEntityTriggerImportCron";
    String VERSION_ENTITY_TRIGGER_IMPORT_PROGRAMMATICALLY = "versionEntityTriggerImportProgrammatically";
    String VERSION_ENTITY_TRIGGER_CLEAN_CRON = "versionEntityTriggerCleanCron";
    String VERSION_ENTITY_TRIGGER_DELETE_PROGRAMMATICALLY = "versionEntityTriggerDeleteProgrammatically";
}
