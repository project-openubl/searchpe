package io.github.carlosthe19916.repeid.repository;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.model.Version;

import java.util.Optional;

public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> getCompany(long id);

    Optional<Company> getCompanyByRuc(Version version, String ruc);
}
