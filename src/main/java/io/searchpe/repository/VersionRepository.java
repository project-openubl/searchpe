package io.searchpe.repository;

import io.searchpe.model.Version;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface VersionRepository {

    Optional<Version> getLastVersion();

    List<Version> getVersionByIssueDate(Date date);

    void deleteVersion(Version version);

}
