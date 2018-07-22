package io.searchpe.batchs.clean;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.BatchStatus;

import java.util.Properties;

import static org.junit.Assert.*;

@Ignore
public class CleanFilesBatchletTest {

    static JobOperator jobOp = null;

    private static int sleepTime = 3000;

    @BeforeClass
    public static void init() {
        jobOp = BatchRuntime.getJobOperator();
    }

    @Test
    public void testRestartAfterRestartAfterComplete() throws Exception {
        Properties origParams = new Properties();
        origParams.setProperty("file1", "file1.txt");
        origParams.setProperty("file2", "file2.txt");

        long execId = jobOp.start("clean_files", origParams);
//        Thread.sleep(sleepTime);
        assertEquals("Didn't fail as expected", BatchStatus.FAILED, jobOp.getJobExecution(execId).getBatchStatus());
    }

}