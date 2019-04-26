package io.searchpe.models;

import java.util.Optional;

public interface VersionProvider {

    Optional<VersionModel> getLastCompletedVersion();

}
