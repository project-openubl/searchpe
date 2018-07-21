package io.github.carlosthe19916.repeid.repository;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.model.Version;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CompanyRepositoryImpl implements CompanyRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Company save(Company company) {
        if (company.getId() != null) {
            em.persist(company);
        } else {
            em.merge(company);
        }
        return company;
    }

    @Override
    public Optional<Company> getCompany(long id) {
        Company company = em.find(Company.class, id);
        return Optional.ofNullable(company);
    }

    @Override
    public Optional<Company> getCompanyByRuc(Version version, String ruc) {
        TypedQuery<Company> query = em.createNamedQuery("getCompaniesByVersionIdAndRuc", Company.class);
        query.setParameter("versionId", version.getId());
        query.setParameter("ruc", ruc);

        List<Company> resultList = query.getResultList();
        if (resultList.isEmpty()) {
            return Optional.empty();
        } else if (resultList.size() == 1) {
            return Optional.of(resultList.get(0));
        } else {
            throw new IllegalStateException("More than one result");
        }
    }

    @Override
    public List<Company> getCompanyByRazonSocial(Version version, String razonSocial) {
        TypedQuery<Company> query = em.createNamedQuery("getCompaniesByVersionIdAndRazonSocial", Company.class);
        query.setParameter("versionId", version.getId());
        query.setParameter("razonSocial", "%" + razonSocial.toLowerCase());

        return query.getResultList();
    }

    @Override
    public List<Company> getCompanyByFilterText(Version version, String filterText) {
        TypedQuery<Company> query = em.createNamedQuery("getCompaniesByVersionIdAndFilterText", Company.class);
        query.setParameter("versionId", version.getId().toLowerCase());
        query.setParameter("filterText", "%" + filterText);

        return query.getResultList();
    }

}
