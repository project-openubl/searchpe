package io.github.carlosthe19916.repeid.services;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.model.Version;
import io.github.carlosthe19916.repeid.repository.CompanyRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;

@ApplicationScoped
public class CompanyServiceImpl implements CompanyService {

    @Inject
    private CompanyRepository companyRepository;

    @Override
    public Optional<Company> getCompany(long id) {
        return companyRepository.getCompany(id);
    }

    @Override
    public Optional<Company> getCompanyByRuc(Version version, String ruc) {
        return companyRepository.getCompanyByRuc(version, ruc);
    }

}
