package io.searchpe.models;

import java.util.Optional;

public interface VersionProvider {

    VersionModel addVersion();

    Optional<VersionModel> getActiveVersion();

}
