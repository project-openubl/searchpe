package io.github.carlosthe19916.repeid.batchs.persist;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.model.Version;
import org.jberet.support.io.JpaItemWriter;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
public class TxtWriter extends JpaItemWriter {

    @Inject
    private TxtVersion txtVersion;

    @Override
    public void writeItems(final List<Object> items) throws Exception {
        if (entityTransaction) {
            em.getTransaction().begin();
        }

        Version version = txtVersion.getVersion();

        for (final Object e : items) {
            Company company = (Company) e;
            company.setVersion(version);
            em.persist(company);
        }

        if (entityTransaction) {
            em.getTransaction().commit();
        }
    }

}
