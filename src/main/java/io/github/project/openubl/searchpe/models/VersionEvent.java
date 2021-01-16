package io.github.project.openubl.searchpe.models;

public interface VersionEvent {
    interface DownloadingEvent {
        Long getVersion();
    }

    interface UnzippingFileEvent {
        Long getVersion();
    }

    interface ImportingDataEvent {
        Long getVersion();
    }
}
