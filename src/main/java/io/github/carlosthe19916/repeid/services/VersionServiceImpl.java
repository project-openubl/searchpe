package io.github.carlosthe19916.repeid.services;

import io.github.carlosthe19916.repeid.model.Version;
import io.github.carlosthe19916.repeid.repository.VersionRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;

@ApplicationScoped
public class VersionServiceImpl implements VersionService {

    @Inject
    private VersionRepository versionRepository;

    @Override
    public Optional<Version> getLastVersion() {
        return versionRepository.getLastVersion();
    }
}
