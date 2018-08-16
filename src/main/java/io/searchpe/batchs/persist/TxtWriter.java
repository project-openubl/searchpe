package io.searchpe.batchs.persist;

import io.searchpe.model.Company;
import io.searchpe.model.Version;
import org.jberet.support.io.JpaItemWriter;

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import java.util.List;

@Named
public class TxtWriter extends JpaItemWriter {

    @Inject
    private TxtVersion txtVersion;

    @Override
    public void writeItems(final List<Object> items) throws Exception {
        if (entityTransaction) {
            getEntityManager().getTransaction().begin();
        }

        Version version = getTxtVersion().getVersion();

        for (final Object e : items) {
            Company company = (Company) e;
            company.setVersion(version);
            getEntityManager().persist(company);
        }

        if (entityTransaction) {
            getEntityManager().getTransaction().commit();
        }
    }

    public EntityManager getEntityManager() {
        return em;
    }

    public TxtVersion getTxtVersion() {
        return txtVersion;
    }

    public void setTxtVersion(TxtVersion txtVersion) {
        this.txtVersion = txtVersion;
    }
}
