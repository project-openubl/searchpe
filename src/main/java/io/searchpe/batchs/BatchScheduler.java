package io.searchpe.batchs;

import org.jboss.logging.Logger;
import org.wildfly.swarm.spi.runtime.annotations.ConfigurationValue;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.batch.runtime.BatchRuntime;
import javax.ejb.*;
import javax.ejb.Timer;
import javax.inject.Inject;
import java.nio.charset.Charset;
import java.util.*;

@Startup
@Singleton
public class BatchScheduler {

    private static final Logger logger = Logger.getLogger(BatchScheduler.class);

    @Resource
    private TimerService timerService;

    @Inject
    @ConfigurationValue("repeid.scheduler.enabled")
    private Optional<Boolean> schedulerEnabled;

    @Inject
    @ConfigurationValue("repeid.scheduler.initialExpiration")
    private Optional<String> schedulerInitialExpiration;

    @Inject
    @ConfigurationValue("repeid.scheduler.timeZone")
    private Optional<String> schedulerTimeZone;

    @Inject
    @ConfigurationValue("repeid.scheduler.intervalDuration")
    private Optional<Long> schedulerIntervalDuration;

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
    @ConfigurationValue("repeid.scheduler.expirationTimeInMillis")
    private Optional<Integer> schedulerExpirationTimeInMillis;

    @PostConstruct
    public void initialize() {
        if (schedulerEnabled.isPresent() && schedulerEnabled.get()) {
            long intervalDuration = schedulerIntervalDuration.orElse(3_600_000L); // One hour
            Timer timer;
            if (schedulerInitialExpiration.isPresent()) {
                String[] time = schedulerInitialExpiration.get().split(":");

                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR, Integer.parseInt(time[0]));
                calendar.set(Calendar.MINUTE, Integer.parseInt(time[1]));
                calendar.set(Calendar.SECOND, Integer.parseInt(time[2]));

                schedulerTimeZone.ifPresent(timeZone -> calendar.setTimeZone(TimeZone.getTimeZone(timeZone)));
                Date initialExpirationDate = calendar.getTime();

                logger.infof("Creating timer from time");
                logger.infof("Creating timer initialDayExpiration[%s], intervalDuration[%s]", initialExpirationDate, intervalDuration);
                timer = timerService.createTimer(initialExpirationDate, intervalDuration, null);
            } else {
                long initialDuration = 5 * 1000L; // 5 Seconds
                logger.infof("Creating default timer");
                logger.infof("Creating timer initialDuration[%s], intervalDuration[%s]", initialDuration, intervalDuration);
                timer = timerService.createTimer(initialDuration, intervalDuration, null);
            }

            logger.infof("Timer Next Timeout at %s", timer.getNextTimeout());
            logger.infof("Time remaining %s", timer.getTimeRemaining());
        } else {
            logger.infof("Scheduler disabled, this node will not execute schedulers");
        }
    }

    @Timeout
    public void programmaticTimeout(Timer timer) {
        logger.infof("Scheduler execution...");

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
        properties.put("expirationTimeInMillis", schedulerExpirationTimeInMillis.orElse(0));

        BatchRuntime.getJobOperator().start("update_database", properties);
    }
}