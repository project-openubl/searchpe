package io.searchpe.models;

import java.util.List;
import java.util.Optional;

public interface CompanyProvider {

    Optional<CompanyModel> getCompany(long id);

    Optional<CompanyModel> getCompanyByRuc(VersionModel version, String ruc);

    List<CompanyModel> getCompanyByFilterText(VersionModel version, String filterText, int first, int max);

}
