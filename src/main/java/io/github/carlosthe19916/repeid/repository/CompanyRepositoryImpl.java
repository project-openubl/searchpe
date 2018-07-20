package io.github.carlosthe19916.repeid.repository;

import io.github.carlosthe19916.repeid.model.Company;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

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

}
