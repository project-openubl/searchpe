package io.searchpe.batchs.persist;

import io.searchpe.model.Version;
import io.searchpe.services.VersionService;
import org.jberet.support._private.SupportMessages;
import org.jboss.logging.Logger;

import javax.annotation.Resource;
import javax.batch.api.BatchProperty;
import javax.batch.api.listener.StepListener;
import javax.batch.runtime.BatchStatus;
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
import javax.transaction.UserTransaction;
import java.util.*;

@Named
public class TxtListener implements StepListener {

    private static final Logger logger = Logger.getLogger(TxtListener.class);

    @Inject
    private VersionService versionService;

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

    private EntityManagerFactory emf;
    private EntityManager em;

    @Resource
    private UserTransaction userTransaction;

    @Override
    public void beforeStep() throws Exception {
        initEntityManager();
        userTransaction.begin();


        Version version = new Version();
        version.setId(UUID.randomUUID().toString());
        version.setDate(Calendar.getInstance().getTime());
        version.setComplete(false);
        version.setNumber(1);

        Optional<Version> lastVersion = versionService.getLastVersion();
        lastVersion.ifPresent(c -> version.setNumber(c.getNumber() + 1));

        em.persist(version);
        userTransaction.commit();

        logger.infof("Version number[%s], complete[%s] created", version.getNumber(), version.isComplete());

        // Save on context
        txtVersion.setVersion(version);
    }

    @Override
    public void afterStep() throws Exception {
        userTransaction.begin();


        BatchStatus batchStatus = stepContext.getBatchStatus();
        Version version = txtVersion.getVersion();
        if (batchStatus.equals(BatchStatus.COMPLETED)) {
            version.setComplete(true);
        }

        Map<String, Long> metrics = new HashMap<>();
        for (Metric metric : stepContext.getMetrics()) {
            metrics.put(metric.getType().toString(), metric.getValue());
            logger.info(metric.getType().toString() + ":" + metric.getValue());
        }
        version.setMetrics(metrics);


        em.merge(version);
        userTransaction.commit();
        closeEntityManager();

        logger.infof("Version number[%s] updated with complete[%s]", version.getNumber(), version.isComplete());
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

}
