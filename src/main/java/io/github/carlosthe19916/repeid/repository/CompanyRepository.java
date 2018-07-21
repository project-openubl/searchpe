package io.github.carlosthe19916.repeid.repository;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.model.Version;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> getCompany(long id);

    Optional<Company> getCompanyByRuc(Version version, String ruc);

    List<Company> getCompanyByRazonSocial(Version version, String razonSocial);

    List<Company> getCompanyByFilterText(Version version, String filterText);
}
