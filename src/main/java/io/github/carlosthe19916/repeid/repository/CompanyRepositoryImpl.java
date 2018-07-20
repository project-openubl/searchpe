package io.github.carlosthe19916.repeid.repository;

import io.github.carlosthe19916.repeid.model.Company;

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
    public Optional<Company> getCompanyByRuc(String ruc) {
        TypedQuery<Company> query = em.createNamedQuery("getCompaniesByRuc", Company.class);
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

}
