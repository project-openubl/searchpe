package io.searchpe.repository;

import io.searchpe.model.Version;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class VersionRepositoryImpl implements VersionRepository {

    @PersistenceContext(unitName = "RepeidPU")
    private EntityManager em;

    @Override
    public Optional<Version> getLastVersion() {
        TypedQuery<Version> query = em.createNamedQuery("getVersions", Version.class);
        query.setMaxResults(1);
        List<Version> resultList = query.getResultList();
        if (resultList.size() == 1) {
            return Optional.of(resultList.get(0));
        }
        return Optional.empty();
    }

    @Override
    public List<Version> getVersionByIssueDate(Date date) {
        TypedQuery<Version> query = em.createNamedQuery("getVersionByIssueDate", Version.class);
        query.setParameter("date", date);
        return query.getResultList();
    }

    @Override
    public void deleteVersion(Version version) {
        em.remove(version);
    }
}
