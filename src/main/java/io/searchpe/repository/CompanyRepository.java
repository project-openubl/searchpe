package io.searchpe.repository;

import io.searchpe.model.Company;
import io.searchpe.model.Version;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> getCompany(long id);

    Optional<Company> getCompanyByRuc(Version version, String ruc);

    List<Company> getCompanyByRazonSocial(Version version, String razonSocial);

    List<Company> getCompanyByFilterText(Version version, String filterText);

    List<Company> getCompanyByFilterText(Version version, String filterText, int first, int max);

}
