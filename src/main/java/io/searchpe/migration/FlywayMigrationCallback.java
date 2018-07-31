package io.searchpe.migration;

import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.callback.BaseFlywayCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.ConfigurationAware;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.Search;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.sql.Connection;

public class FlywayMigrationCallback implements org.flywaydb.core.api.callback.Callback {

    private final SessionFactoryImplementor sessionFactory;

    public FlywayMigrationCallback(SessionFactoryImplementor sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return false;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        EntityManager em = sessionFactory.createEntityManager();
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(em);
        try {
            fullTextEntityManager.createIndexer().startAndWait();
        } catch (InterruptedException e) {
            throw new MigrationException(e);
        }
        
        return false;
    }

    @Override
    public void handle(Event event, Context context) {

    }

    public class MigrationException extends RuntimeException {
        public MigrationException(Throwable cause) {
            super(cause);
        }
    }
}
