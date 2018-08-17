package io.searchpe.batchs.persist;

import io.searchpe.batchs.BatchConstants;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class TxtReaderTest2 {

    static final String jobName = "io.searchpe.batchs.persist.TxtReaderTest";
    private final JobOperator jobOperator = BatchRuntime.getJobOperator();

    public static List<Object> items;

    @Test
    public void emulateBatch() throws Exception {
        Assert.assertEquals(1, 1);
        final long jobExecutionId = jobOperator.start(jobName, null);
    }

}