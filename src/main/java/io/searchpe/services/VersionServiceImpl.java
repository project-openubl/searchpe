package io.searchpe.services;

import io.searchpe.model.Version;
import io.searchpe.repository.VersionRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
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
    public List<Version> getVersionByIssueDate(Date date) {
        return versionRepository.getVersionByIssueDate(date);
    }

    @Override
    public void deleteVersion(Version version) {
        versionRepository.deleteVersion(version);
    }
}
