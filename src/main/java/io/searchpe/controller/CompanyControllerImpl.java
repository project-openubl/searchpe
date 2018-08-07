package io.searchpe.controller;

import io.searchpe.model.Company;
import io.searchpe.model.Version;
import io.searchpe.services.CompanyService;
import io.searchpe.services.VersionService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CompanyControllerImpl implements CompanyController {

    @Inject
    private VersionService versionService;

    @Inject
    private CompanyService companyService;

    @Override
    public List<Company> getCompanies(String ruc, String razonSocial, String filterText, int first, int max) {
        Version lastVersion = versionService.getLastCompletedVersion().orElseThrow(NotFoundException::new);

        if (ruc != null) {
            Optional<Company> company = companyService.getCompanyByRuc(lastVersion, ruc);
            List<Company> companies = new ArrayList<>();
            company.ifPresent(companies::add);
            return companies;
        } else if (razonSocial != null) {
            return companyService.getCompanyByRazonSocial(lastVersion, razonSocial);
        } else if (filterText != null) {
            return companyService.getCompanyByFilterText(lastVersion, filterText, first, max);
        }

        throw new BadRequestException();
    }

    public Company getCompanyById(Long id) {
        return companyService.getCompany(id).orElseThrow(NotFoundException::new);
    }

}
