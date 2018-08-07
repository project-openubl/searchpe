package io.searchpe.services;

import io.searchpe.model.Company;
import io.searchpe.model.Version;
import io.searchpe.repository.CompanyRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
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

    @Override
    public List<Company> getCompanyByRazonSocial(Version version, String razonSocial) {
        return companyRepository.getCompanyByRazonSocial(version, razonSocial);
    }

    @Override
    public List<Company> getCompanyByFilterText(Version version, String filterText) {
        return companyRepository.getCompanyByFilterText(version, filterText);
    }

    @Override
    public List<Company> getCompanyByFilterText(Version version, String filterText, int first, int max) {
        return companyRepository.getCompanyByFilterText(version, filterText, first, max);
    }

}
