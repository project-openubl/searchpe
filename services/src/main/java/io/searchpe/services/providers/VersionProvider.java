package io.searchpe.services.providers;

import io.searchpe.services.jpa.entity.VersionEntity;

import java.util.Optional;

public interface VersionProvider {

    Optional<VersionEntity> getActive();

    Optional<VersionEntity> getById(Long id);
}
