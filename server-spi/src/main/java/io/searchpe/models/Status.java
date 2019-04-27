package io.searchpe.models;

public enum Status {
    NOT_PROCESSED,
    IMPORT_IN_PROGRESS,
    IMPORT_FINISHED_SUCCESSFULLY,
    IMPORT_FINISHED_WITH_ERRORS;

    public static Status getDefault() {
        return Status.NOT_PROCESSED;
    }

}
