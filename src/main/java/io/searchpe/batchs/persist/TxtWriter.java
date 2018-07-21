package io.searchpe.batchs.persist;

import io.searchpe.model.Company;
import io.searchpe.model.Version;
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
