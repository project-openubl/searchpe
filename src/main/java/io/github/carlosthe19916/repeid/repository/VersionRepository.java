package io.github.carlosthe19916.repeid.repository;

import io.github.carlosthe19916.repeid.model.Version;

import java.util.Optional;

public interface VersionRepository {

    Optional<Version> getLastVersion();

}
