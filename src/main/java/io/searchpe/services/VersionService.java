package io.searchpe.services;

import io.searchpe.model.Version;

import java.util.Optional;

public interface VersionService {

    Optional<Version> getLastVersion();

}
