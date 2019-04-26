package io.searchpe.services.resources;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

//@Path("/companies")
//@ApplicationScoped
public class CompanyResource {

//    @Inject
//    private VersionProvider versionProvider;
//
//    @Inject
//    private CompanyProvider companyProvider;
//
//    @GET
//    @Path("/")
//    @Produces("application/json")
//    public List<CompanyRepresentation> getCompanies(
//            @QueryParam("ruc") String ruc,
//            @QueryParam("razonSocial") String razonSocial,
//            @QueryParam("filterText") String filterText,
//            @QueryParam("first") @DefaultValue("0") int first,
//            @QueryParam("max") @DefaultValue("10") int max
//    ) {
//        VersionModel lastVersion = versionProvider.getLastCompletedVersion().orElseThrow(NotFoundException::new);
//
//        if (ruc != null) {
//            Optional<CompanyModel> company = companyProvider.getCompanyByRuc(lastVersion, ruc);
//            List<CompanyModel> companies = new ArrayList<>();
//            company.ifPresent(companies::add);
//
//            return companies.stream()
//                    .map(ModelToRepresentation::toRepresentation)
//                    .collect(Collectors.toList());
//        } else if (razonSocial != null) {
//            return companyProvider.getCompanyByRazonSocial(lastVersion, razonSocial)
//                    .stream()
//                    .map(ModelToRepresentation::toRepresentation)
//                    .collect(Collectors.toList());
//        } else if (filterText != null) {
//            return companyProvider.getCompanyByFilterText(lastVersion, filterText, first, max)
//                    .stream()
//                    .map(ModelToRepresentation::toRepresentation)
//                    .collect(Collectors.toList());
//        }
//
//        throw new BadRequestException();
//    }
//
//    @GET
//    @Path("/{id}")
//    @Produces("application/json")
//    public CompanyRepresentation getCompanyById(@PathParam("id") Long id) {
//        CompanyModel company = companyProvider.getCompany(id).orElseThrow(NotFoundException::new);
//        return ModelToRepresentation.toRepresentation(company);
//    }

}
