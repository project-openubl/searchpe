package io.github.carlosthe19916.repeid.batchs.persist;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.model.Version;
import org.jberet.support.io.JpaItemWriter;

import javax.inject.Named;
import javax.persistence.TypedQuery;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Named
public class TxtWriter extends JpaItemWriter {

    @Override
    public void writeItems(final List<Object> items) throws Exception {
        if (entityTransaction) {
            em.getTransaction().begin();
        }

        Version version = new Version();
        version.setId(UUID.randomUUID().toString());
        version.setDate(Calendar.getInstance().getTime());
        version.setNumber(1);

        Optional<Version> lastVersion = getLastVersion();
        lastVersion.ifPresent(c -> version.setNumber(c.getNumber() + 1));

        em.persist(version);

        for (final Object e : items) {
            Company company = (Company) e;
            company.setVersion(version);
            em.persist(company);
        }

        if (entityTransaction) {
            em.getTransaction().commit();
        }
    }

    private Optional<Version> getLastVersion() {
        TypedQuery<Version> query = em.createNamedQuery("getVersions", Version.class);
        query.setMaxResults(1);
        List<Version> resultList = query.getResultList();
        if (resultList.size() == 1) {
            return Optional.of(resultList.get(0));
        }
        return Optional.empty();
    }

}
