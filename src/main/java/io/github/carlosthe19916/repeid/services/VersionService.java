package io.github.carlosthe19916.repeid.services;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.model.Version;

import java.util.Optional;

public interface VersionService {

    Optional<Version> getLastVersion();

}
