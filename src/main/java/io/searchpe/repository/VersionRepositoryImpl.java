package io.searchpe.repository;

import io.searchpe.model.Company;
import io.searchpe.model.Version;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.*;
import java.util.function.Function;

@ApplicationScoped
public class VersionRepositoryImpl implements VersionRepository {

    @PersistenceContext(unitName = "RepeidPU")
    private EntityManager em;

    private final static Function<String, String> fieldsFunction = VersionRepositoryImpl::fieldName;

    private static String fieldName(String fieldName) {
        return fieldName;
    }

    @Override
    public Optional<Version> getLastCompletedVersion() {
        TypedQuery<Version> query = em.createNamedQuery("getVersionsByCompleteStatus", Version.class);
        query.setParameter("complete", true);
        query.setMaxResults(1);
        List<Version> resultList = query.getResultList();
        if (resultList.size() == 1) {
            return Optional.of(resultList.get(0));
        }
        return Optional.empty();
    }

    @Override
    public List<Version> getVersionsBefore(Date date) {
        TypedQuery<Version> query = em.createNamedQuery("getVersionsBefore", Version.class);
        query.setParameter("date", date);
        return query.getResultList();
    }

    @Override
    public boolean deleteVersion(Version version) {
        em.createNamedQuery("deleteCompaniesByVersionId")
                .setParameter("versionId", version.getId())
                .executeUpdate();
        em.remove(version);
        return true;
    }

    @Override
    public List<Version> getVersionsByParameters(Map<String, Object> parameters) {
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Version> query = criteriaBuilder.createQuery(Version.class);
        Root<Version> root = query.from(Version.class);

        List<Predicate> predicates = new ArrayList<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String fieldName = fieldsFunction.apply(key);
            Predicate predicate = criteriaBuilder.equal(root.get(fieldName), value);
            predicates.add(predicate);
        }

        CriteriaQuery<Version> criteriaQuery = query.select(root);
        criteriaQuery.where(predicates.toArray(new Predicate[]{}));

        TypedQuery<Version> typedQuery = em.createQuery(criteriaQuery);
        return typedQuery.getResultList();
    }
}
