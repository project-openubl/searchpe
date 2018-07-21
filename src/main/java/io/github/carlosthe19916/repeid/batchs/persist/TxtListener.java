package io.github.carlosthe19916.repeid.batchs.persist;

import io.github.carlosthe19916.repeid.model.Version;
import org.jberet.support._private.SupportMessages;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.listener.ChunkListener;
import javax.batch.runtime.Metric;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import java.util.*;
import java.util.stream.Stream;

@Named
public class TxtListener implements ChunkListener {

    @Inject
    private StepContext stepContext;

    @Inject
    private TxtVersion txtVersion;

    @Inject
    protected Instance<EntityManager> entityManagerInstance;

    @Inject
    @BatchProperty
    protected String entityManagerLookupName;

    @Inject
    @BatchProperty
    protected String persistenceUnitName;

    @Inject
    @BatchProperty
    protected Map persistenceUnitProperties;

    @Inject
    @BatchProperty
    protected boolean entityTransaction;

    private EntityManagerFactory emf;
    private EntityManager em;

    @PostConstruct
    protected void postConstruct() {
        initEntityManager();
    }

    @PreDestroy
    protected void preDestroy() {
        closeEntityManager();
    }

    @Override
    public void beforeChunk() throws Exception {
        if (entityTransaction) {
            em.getTransaction().begin();
        }


        Version version = new Version();
        version.setId(UUID.randomUUID().toString());
        version.setDate(Calendar.getInstance().getTime());
        version.setComplete(false);
        version.setNumber(1);

        Optional<Version> lastVersion = getLastVersion();
        lastVersion.ifPresent(c -> version.setNumber(c.getNumber() + 1));

        em.persist(version);


        if (entityTransaction) {
            em.getTransaction().commit();
        }


        txtVersion.setVersion(version);
    }

    @Override
    public void afterChunk() throws Exception {
        if (entityTransaction) {
            em.getTransaction().begin();
        }

        Version version = txtVersion.getVersion();
        version.setComplete(true);

        Map<String, Long> metrics = new HashMap<>();
        for (Metric metric : stepContext.getMetrics()) {
            metrics.put(metric.getType().toString(), metric.getValue());
        }
        version.setMetrics(metrics);

        em.merge(version);

        if (entityTransaction) {
            em.getTransaction().commit();
        }
    }

    @Override
    public void onError(Exception ex) throws Exception {
    }

    private void initEntityManager() {
        if (em == null) {
            if (entityManagerLookupName != null) {
                InitialContext ic = null;
                try {
                    ic = new InitialContext();
                    em = (EntityManager) ic.lookup(entityManagerLookupName);
                } catch (final NamingException e) {
                    throw SupportMessages.MESSAGES.failToLookup(e, entityManagerLookupName);
                } finally {
                    if (ic != null) {
                        try {
                            ic.close();
                        } catch (final NamingException e) {
                            //ignore
                        }
                    }
                }
            } else {
                if (entityManagerInstance != null && !entityManagerInstance.isUnsatisfied()) {
                    em = entityManagerInstance.get();
                }
                if (em == null) {
                    emf = Persistence.createEntityManagerFactory(persistenceUnitName, persistenceUnitProperties);
                    em = emf.createEntityManager();
                }
            }
        }
    }

    private void closeEntityManager() {
        if (emf != null) {
            em.close();
            emf.close();
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
