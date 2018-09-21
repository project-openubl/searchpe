package io.searchpe.batchs;

import io.searchpe.model.Version;
import io.searchpe.services.VersionService;
import io.searchpe.utils.DateUtils;
import io.searchpe.utils.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jboss.logging.Logger;
import org.wildfly.swarm.spi.runtime.annotations.ConfigurationValue;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.batch.runtime.BatchRuntime;
import javax.ejb.*;
import javax.ejb.Timer;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Startup
@Singleton
public class BatchScheduler {

    public static final String DEFAULT_WORKING_DIRECTORY = "searchpe_working_directory";
    public static final String DEFAULT_EXECUTION_TIME = "00:00:00";
    public static final String SUNAT_ZIP_FILE_NAME = "padron_reducido_ruc.zip";
    public static final int READ_TIMEOUT = 10_000;
    public static final int CONNECTION_TIMEOUT = 10_000;

    private static final Logger logger = Logger.getLogger(BatchScheduler.class);

    @Resource
    private TimerService timerService;

    @Inject
    private VersionService versionService;

    @Inject
    @ConfigurationValue("searchpe.scheduler.enabled")
    private Optional<Boolean> schedulerEnabled;

    @Inject
    @ConfigurationValue("searchpe.scheduler.time")
    private Optional<String> schedulerTime;

    @Inject
    @ConfigurationValue("searchpe.scheduler.timeZone")
    private Optional<String> schedulerTimeZone;

    @Inject
    @ConfigurationValue("searchpe.scheduler.intervalDuration")
    private Optional<Long> intervalDuration;

    @Inject
    @ConfigurationValue("searchpe.scheduler.workingDirectory")
    private String workingDirectory;

    @Inject
    @ConfigurationValue("searchpe.scheduler.sunat.zipURL")
    private String sunatZipURL;

    @Inject
    @ConfigurationValue("searchpe.scheduler.deleteIncompleteVersions")
    private Optional<Boolean> deleteIncompleteVersions;

    @Inject
    @ConfigurationValue("searchpe.scheduler.maxNumberOfVersions")
    private Optional<Integer> maxNumberOfVersions;

    @PostConstruct
    public void initialize() {
        Optional<Version> lastCompletedVersion = versionService.getLastCompletedVersion();
        if (!lastCompletedVersion.isPresent()) {
            startBatch();
        }

        if (schedulerEnabled.isPresent() && schedulerEnabled.get()) {
            ZoneId zoneId = ZoneId.systemDefault();
            if (schedulerTimeZone.isPresent()) {
                zoneId = ZoneId.of(schedulerTimeZone.get());
            }

            ZonedDateTime currentDateTime = ZonedDateTime.now(zoneId);
            LocalTime executionTime = LocalTime.parse(schedulerTime.orElse(DEFAULT_EXECUTION_TIME));
            ZonedDateTime nextExecutionDateTime = DateUtils.getNextDate(currentDateTime, executionTime);

            Timer timer = timerService.createTimer(
                    Date.from(nextExecutionDateTime.toInstant()),
                    intervalDuration.orElse(86_400_000L),
                    null);

            long timeRemaining = timer.getTimeRemaining();
            logger.infof("Timer Next Timeout at %s", timer.getNextTimeout());
            logger.infof("Time remaining %s milliseconds [%s hours %s minutes %s seconds]",
                    timeRemaining,
                    TimeUnit.MILLISECONDS.toHours(timeRemaining),
                    TimeUnit.MILLISECONDS.toMinutes(timeRemaining),
                    TimeUnit.MILLISECONDS.toSeconds(timeRemaining)
            );

        } else {
            logger.infof("Scheduler disabled, this node will not execute schedulers");
        }
    }

    @Timeout
    public void programmaticTimeout(Timer timer) {
        startBatch();
    }

    public Version beforeStep() {
        Version version = new Version();
        version.setId(UUID.randomUUID().toString());
        version.setDate(Calendar.getInstance().getTime());
        version.setComplete(false);
        version.setNumber(1);

        Optional<Version> lastVersion = versionService.getLastVersion();
        lastVersion.ifPresent(c -> version.setNumber(c.getNumber() + 1));

        return versionService.createVersion(version);
    }

    private void startBatch() {
        if (sunatZipURL == null) {
            throw new IllegalStateException("SUNAT URL not defined");
        }

        File fileWorkingDirectory;
        if (workingDirectory != null) {
            fileWorkingDirectory = new File(workingDirectory);
        } else {
            fileWorkingDirectory = new File(DEFAULT_WORKING_DIRECTORY);
        }
        if (!fileWorkingDirectory.exists()) {
            fileWorkingDirectory.mkdir();
        }
        Path workingDirectoryPath = fileWorkingDirectory.toPath();

        // Cleaning
        logger.infof("Cleaning %s", fileWorkingDirectory.getAbsolutePath());
        try {
            org.apache.commons.io.FileUtils.cleanDirectory(fileWorkingDirectory);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        // Downloading
        File downloadedFile;
        try {
            downloadedFile = workingDirectoryPath.resolve(SUNAT_ZIP_FILE_NAME).toFile();
            logger.infof("Downloading %s into %s", sunatZipURL, downloadedFile);
            org.apache.commons.io.FileUtils.copyURLToFile(new URL(sunatZipURL), downloadedFile, CONNECTION_TIMEOUT, READ_TIMEOUT);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        // Unzip
        File txtFile = null;
        try {
            Path unzipFolderPath = workingDirectoryPath.resolve("unzipFolder");
            logger.infof("Unzipping %s content into %s", unzipFolderPath.getFileName(), unzipFolderPath.getFileName());
            FileUtils.unzipFile(downloadedFile, unzipFolderPath);

            File[] files = unzipFolderPath.toFile().listFiles();
            if (files != null) {
                for (File file : files) {
                    String extension = FilenameUtils.getExtension(file.getName());
                    if (extension.equalsIgnoreCase("txt")) {
                        txtFile = file;
                        break;
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        if (txtFile == null) {
            throw new IllegalStateException("Could not find any *.txt file to read");
        }

        // Create version
        Version version = beforeStep();

        // Start batch
        Properties properties = new Properties();
        properties.put("deleteIncompleteVersions", deleteIncompleteVersions.orElse(true));
        properties.put("maxNumberOfVersions", maxNumberOfVersions.orElse(0));
        try {
            URL url = txtFile.toURL();
            properties.put("resource", url.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        properties.put("versionId", version.getId());

        BatchRuntime.getJobOperator().start("update_database", properties);
    }

}
