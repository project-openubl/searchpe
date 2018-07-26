package io.searchpe.services;

import io.searchpe.model.Version;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface VersionService {

    Optional<Version> getLastVersion();
    List<Version> getVersionByIssueDate(Date date);

    void deleteVersion(Version version);
}
