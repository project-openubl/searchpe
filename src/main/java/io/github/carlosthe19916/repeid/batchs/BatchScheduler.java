package io.github.carlosthe19916.repeid.batchs;

import org.jboss.logging.Logger;

import javax.batch.runtime.BatchRuntime;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.util.Properties;

@Startup
@Singleton
public class BatchScheduler {

    private static final Logger logger = Logger.getLogger(BatchScheduler.class);

//    @Schedule(hour = "3", persistent = false, timezone = "America/Lima")
    @Schedule(hour = "*", minute = "*", second = "*/30", persistent = false)
    public void startBatch() {
        logger.info("Starting batch...");
        Properties properties = new Properties();
        BatchRuntime.getJobOperator().start("update_database", properties);
        logger.info("Finishing batch...");
    }

}
