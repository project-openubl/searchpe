package io.searchpe.models.jpa;

import io.quarkus.panache.common.Page;
import io.searchpe.models.CompanyModel;
import io.searchpe.models.CompanyProvider;
import io.searchpe.models.VersionModel;
import io.searchpe.models.jpa.entity.CompanyEntity;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Transactional
@ApplicationScoped
public class JpaCompanyProvider implements CompanyProvider {

    @Inject
    EntityManager em;

    Function<CompanyEntity, CompanyModel> toModel = companyEntity -> new CompanyAdapter(em, companyEntity);

    @Override
    public Optional<CompanyModel> getCompany(long id) {
        CompanyEntity companyEntity = em.find(CompanyEntity.class, id);
        if (companyEntity == null) {
            return Optional.empty();
        }
        return Optional.of(new CompanyAdapter(em, companyEntity));
    }

    @Override
    public Optional<CompanyModel> getCompanyByRuc(VersionModel version, String ruc) {
        List<CompanyEntity> activeVersions = CompanyEntity.list("ruc", ruc);
        return Utils.transformSingle(activeVersions, toModel);
    }

    @Override
    public List<CompanyModel> getCompanyByFilterText(String filterText, int first, int max) {
        Page page = new Page(max - first);
        List<CompanyEntity> companies = CompanyEntity.find("select c from CompanyEntity c where lower(c.razonSocial) like :filterText")
                .page(page)
                .list();
        return companies.stream()
                .map(companyEntity -> new CompanyAdapter(em, companyEntity))
                .collect(Collectors.toList());
    }

}
