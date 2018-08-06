package io.searchpe.batchs;

import io.searchpe.model.Version;
import io.searchpe.services.VersionService;
import io.searchpe.utils.DateUtils;
import org.jboss.logging.Logger;
import org.wildfly.swarm.spi.runtime.annotations.ConfigurationValue;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.batch.runtime.BatchRuntime;
import javax.ejb.*;
import javax.inject.Inject;
import java.nio.charset.Charset;
import java.time.LocalTime;
import java.util.Date;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Startup
@Singleton
public class BatchScheduler {

    private static final Logger logger = Logger.getLogger(BatchScheduler.class);

    @Resource
    private TimerService timerService;

    @Inject
    private VersionService versionService;

    @Inject
    @ConfigurationValue("searchpe.scheduler.enabled")
    private Optional<Boolean> schedulerEnabled;

    @Inject
    @ConfigurationValue("searchpe.scheduler.initialExpiration")
    private Optional<String> initialExpiration;

    @Inject
    @ConfigurationValue("searchpe.scheduler.timeZone")
    private Optional<String> timeZone;

    @Inject
    @ConfigurationValue("searchpe.scheduler.intervalDuration")
    private Optional<Long> intervalDuration;

    @Inject
    @ConfigurationValue("searchpe.scheduler.sunat.zipFileName")
    private Optional<String> sunatZipFileName;

    @Inject
    @ConfigurationValue("searchpe.scheduler.sunat.unzipFileName")
    private Optional<String> sunatUnzipFileName;

    @Inject
    @ConfigurationValue("searchpe.scheduler.sunat.zipURL")
    private String sunatZipURL;

    @Inject
    @ConfigurationValue("searchpe.scheduler.sunat.txtCharset")
    private Optional<String> sunatTxtCharset;

    @Inject
    @ConfigurationValue("searchpe.scheduler.sunat.txtRowSkips")
    private Optional<Long> sunatTxtRowSkips;

    @Inject
    @ConfigurationValue("searchpe.scheduler.sunat.txtColumnSplitRegex")
    private String sunatTxtColumnSplitRegex;

    @Inject
    @ConfigurationValue("searchpe.scheduler.sunat.txtHeadersTemplate")
    private String sunatTxtHeadersTemplate;

    @Inject
    @ConfigurationValue("searchpe.scheduler.sunat.modelHeadersTemplate")
    private String sunatModelHeadersTemplate;

    @Inject
    @ConfigurationValue("searchpe.scheduler.deleteIncompleteVersions")
    private Optional<Boolean> deleteIncompleteVersions;

    @Inject
    @ConfigurationValue("searchpe.scheduler.expirationTimeInMillis")
    private Optional<Integer> expirationTimeInMillis;

    @PostConstruct
    public void initialize() {
        Optional<Version> lastCompletedVersion = versionService.getLastCompletedVersion();
        if (!lastCompletedVersion.isPresent()) {
            startBatch();
        }

        if (schedulerEnabled.isPresent() && schedulerEnabled.get()) {
            long defaultIntervalDuration = intervalDuration.orElse(86_400_000L); // 24 hours

            Timer timer;
            if (initialExpiration.isPresent()) {
                LocalTime time = LocalTime.parse(initialExpiration.get());
                Date initialExpirationDate = DateUtils.getNearestFutureExpirationDate(time);

                logger.infof("Creating timer from time");
                logger.infof("Creating timer initialDayExpiration[%s], intervalDuration[%s]", initialExpirationDate, defaultIntervalDuration);
                timer = timerService.createTimer(initialExpirationDate, defaultIntervalDuration, null);
            } else {
                logger.infof("Creating default timer");
                logger.infof("Creating timer initialDuration[%s], intervalDuration[%s]", defaultIntervalDuration, defaultIntervalDuration);
                timer = timerService.createTimer(defaultIntervalDuration, defaultIntervalDuration, null);
            }

            long timeRemaining = timer.getTimeRemaining();
            Date nextTimeout = timer.getNextTimeout();
            logger.infof("Timer Next Timeout at %s", nextTimeout);
            logger.infof("Time remaining %s milliseconds [%s hours %s minutes %s seconds]", timeRemaining, TimeUnit.MILLISECONDS.toHours(timeRemaining), TimeUnit.MILLISECONDS.toMinutes(timeRemaining), TimeUnit.MILLISECONDS.toSeconds(timeRemaining));
        } else {
            logger.infof("Scheduler disabled, this node will not execute schedulers");
        }
    }

    @Timeout
    public void programmaticTimeout(Timer timer) {
        startBatch();
    }

    private void startBatch() {
        logger.infof("Scheduler execution...");
        Properties properties = new Properties();

        properties.put("deleteIncompleteVersions", deleteIncompleteVersions.orElse(false));
        properties.put("expirationTimeInMillis", expirationTimeInMillis.orElse(0));

        properties.put("sunatZipURL", sunatZipURL);
        properties.put("sunatZipFileName", sunatZipFileName.orElse(UUID.randomUUID().toString() + ".zip"));
        properties.put("sunatUnzipFileName", sunatUnzipFileName.orElse(UUID.randomUUID().toString() + ".txt"));
        properties.put("sunatTxtCharset", sunatTxtCharset.orElse(Charset.defaultCharset().name()));
        properties.put("sunatTxtRowSkips", sunatTxtRowSkips.orElse(1L));
        properties.put("sunatTxtColumnSplitRegex", sunatTxtColumnSplitRegex);
        properties.put("sunatTxtHeadersTemplate", sunatTxtHeadersTemplate);
        properties.put("sunatModelHeadersTemplate", sunatModelHeadersTemplate);

        BatchRuntime.getJobOperator().start("update_database", properties);
    }
}
