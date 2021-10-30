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
