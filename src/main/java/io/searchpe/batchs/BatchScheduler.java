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
    @ConfigurationValue("searchpe.scheduler.enabled")
    private Optional<Boolean> schedulerEnabled;

    @Inject
    @ConfigurationValue("searchpe.scheduler.initialExpiration")
    private Optional<String> schedulerInitialExpiration;

    @Inject
    @ConfigurationValue("searchpe.scheduler.timeZone")
    private Optional<String> schedulerTimeZone;

    @Inject
    @ConfigurationValue("searchpe.scheduler.intervalDuration")
    private Optional<Long> schedulerIntervalDuration;

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

        properties.put("deleteIncompleteVersions", deleteIncompleteVersions.orElse(false));
        properties.put("expirationTimeInMillis", schedulerExpirationTimeInMillis.orElse(0));

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
