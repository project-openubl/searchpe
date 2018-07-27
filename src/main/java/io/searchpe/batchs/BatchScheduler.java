package io.searchpe.batchs;

import org.jboss.logging.Logger;
import org.wildfly.swarm.spi.runtime.annotations.ConfigurationValue;

import javax.batch.runtime.BatchRuntime;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Startup
@Singleton
public class BatchScheduler {

    private static final Logger logger = Logger.getLogger(BatchScheduler.class);

    @Inject
    @ConfigurationValue("repeid.scheduler.enabled")
    private Optional<Boolean> schedulerEnabled;

    @Inject
    @ConfigurationValue("repeid.scheduler.downloadFileLocation")
    private Optional<String> schedulerDownloadFileLocation;

    @Inject
    @ConfigurationValue("repeid.scheduler.unzipFileLocation")
    private Optional<String> schedulerUnzipFileLocation;

    @Inject
    @ConfigurationValue("repeid.scheduler.databaseURL")
    private String schedulerDatabaseURL;

    @Inject
    @ConfigurationValue("repeid.scheduler.fileCharset")
    private Optional<String> schedulerFileCharset;

    @Inject
    @ConfigurationValue("repeid.scheduler.fileRowSkip")
    private Optional<Long> schedulerFileRowSkip;

    @Inject
    @ConfigurationValue("repeid.scheduler.fileColumnSeparator")
    private String schedulerFileColumnSeparator;

    @Inject
    @ConfigurationValue("repeid.scheduler.fileColumnHeaders")
    private String schedulerFileColumnHeaders;

    @Inject
    @ConfigurationValue("repeid.scheduler.fileColumnValues")
    private String schedulerFileColumnValues;

    @Inject
    @ConfigurationValue("repeid.scheduler.purgeIncompleteVersions")
    private Optional<Boolean> schedulerPurgeIncompleteVersions;

    @Inject
    @ConfigurationValue("repeid.scheduler.expirationVersionDays")
    private Optional<Integer> schedulerExpirationVersionDays;

    @Schedule(hour = "12", minute = "49", timezone = "America/Lima", persistent = false)
    public void schedule() {
        if (schedulerEnabled.isPresent() && schedulerEnabled.get()) {
            logger.info("Starting batch...");

            Properties properties = new Properties();
            properties.put("downloadFileLocation", schedulerDownloadFileLocation.orElse(UUID.randomUUID().toString() + ".zip"));
            properties.put("unzipFileLocation", schedulerUnzipFileLocation.orElse(UUID.randomUUID().toString() + ".txt"));
            properties.put("databaseURL", schedulerDatabaseURL);
            properties.put("fileCharset", schedulerFileCharset.orElse(Charset.defaultCharset().name()));
            properties.put("fileRowSkip", schedulerFileRowSkip.orElse(0L));
            properties.put("fileColumnSeparator", schedulerFileColumnSeparator);
            properties.put("fileColumnHeaders", schedulerFileColumnHeaders);
            properties.put("fileColumnValues", schedulerFileColumnValues);
            properties.put("purgeIncompleteVersions", schedulerPurgeIncompleteVersions.orElse(false));
            properties.put("expirationVersionDays", schedulerExpirationVersionDays.orElse(0));

            BatchRuntime.getJobOperator().start("update_database", properties);

            logger.info("Finishing batch...");
        }

    }

}
