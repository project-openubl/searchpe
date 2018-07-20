package io.github.carlosthe19916.repeid.services;

import io.github.carlosthe19916.repeid.model.Company;

import java.util.Optional;

public interface CompanyService {

    Optional<Company> getCompanyByRuc(String ruc);
}
