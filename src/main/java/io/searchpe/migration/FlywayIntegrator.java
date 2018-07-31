package io.searchpe.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.hibernate.boot.Metadata;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.PostgreSQL9Dialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

public class FlywayIntegrator implements Integrator {

    private static final Logger logger = Logger.getLogger(FlywayIntegrator.class);

    @Override
    public void integrate(Metadata metadata, SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        logger.info("Starting Flyway Migration");

        final JdbcServices jdbcServices = serviceRegistry.getService(JdbcServices.class);
        Connection connection;
        DataSource dataSource = null;

        try {
            connection = jdbcServices.getBootstrapJdbcConnectionAccess().obtainConnection();
            final Method method = connection != null ? connection.getClass().getMethod("getDataSource", null) : null;
            dataSource = (DataSource) (method != null ? method.invoke(connection, null) : null);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | SQLException e) {
            logger.error(e);
        }


        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);


        Dialect dialect = jdbcServices.getDialect();
        if (dialect instanceof H2Dialect) {
            flyway.setLocations("classpath:db/migration/h2");
        } else if (dialect instanceof PostgreSQL9Dialect) {
            flyway.setLocations("classpath:db/migration/postgresql");
        } else if (dialect instanceof MySQLDialect) {
            flyway.setLocations("classpath:db/migration/mysql");
        } else {
            throw new IllegalStateException("Not supported Dialect");
        }

        MigrationInfo migrationInfo = flyway.info().current();
        if (migrationInfo == null) {
            logger.info("No existing database at the actual datasource");
        } else {
            logger.infof("Found a database with the version: %s", migrationInfo.getVersion());
        }

        flyway.migrate();
        logger.info("Finished Flyway Migration");
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {

    }

}