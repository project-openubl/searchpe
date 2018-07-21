package io.searchpe.repository;

import io.searchpe.model.Version;

import java.util.Optional;

public interface VersionRepository {

    Optional<Version> getLastVersion();

}
