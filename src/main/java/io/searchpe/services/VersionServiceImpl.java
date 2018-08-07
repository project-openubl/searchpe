package io.searchpe.services;

import io.searchpe.model.Version;
import io.searchpe.repository.VersionRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class VersionServiceImpl implements VersionService {

    @Inject
    private VersionRepository versionRepository;

    @Override
    public Optional<Version> getLastVersion() {
        return versionRepository.getLastVersion();
    }

    @Override
    public Optional<Version> getLastCompletedVersion() {
        return versionRepository.getLastCompletedVersion();
    }

    @Override
    public List<Version> getCompleteVersionsBefore(Date date) {
        return versionRepository.getCompleteVersionsBefore(date);
    }

    @Override
    public List<Version> getVersionsByParameters(Map<String, Object> parameters) {
        return versionRepository.getVersionsByParameters(parameters);
    }

    @Override
    public boolean deleteVersion(Version version) {
        return versionRepository.deleteVersion(version);
    }
}
