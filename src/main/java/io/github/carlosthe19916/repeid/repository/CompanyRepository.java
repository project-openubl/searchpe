package io.github.carlosthe19916.repeid.repository;

import io.github.carlosthe19916.repeid.model.Company;

import java.util.Optional;

public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> getCompanyByRuc(String ruc);

}
