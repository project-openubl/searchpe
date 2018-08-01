package io.searchpe.batchs.download;

import io.searchpe.utils.FileUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.BatchStatus;
import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Properties;

@RunWith(Arquillian.class)
public class DownloadFileBatchletTest {

    private int sleepTime = 3000;
    private JobOperator jobOperator;

    @Before
    public void before() {
        jobOperator = BatchRuntime.getJobOperator();
    }

    @Deployment
    public static Archive createDeployment() throws Exception {
        URL url = Thread.currentThread().getContextClassLoader().getResource("project-test-defaults.yml");
        Assert.assertNotNull(url);
        File projectDefaults = new File(url.toURI());

        JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);

        deployment.setContextRoot("/");
        deployment.addClasses(DownloadFileBatchlet.class, FileUtils.class);

        deployment.addAsResource(projectDefaults, "/project-defaults.yml");
        deployment.addAsResource("persistence-test.xml", "META-INF/persistence.xml");
        deployment.addAsManifestResource(EmptyAsset.INSTANCE, "WEB-INF/beans.xml");
        deployment.addAsResource("batch-jobs/download_files.xml", "META-INF/batch-jobs/download_files.xml");
        deployment.addAllDependencies();

        return deployment;
    }

    @Test
    public void testDownloadFile() throws Exception {
        Assert.assertFalse(Paths.get("padron_reducido_ruc.zip").toFile().exists());

        Properties properties = new Properties();
        properties.setProperty("url", "https://raw.githubusercontent.com/searchpe/searchpe/master/padron_reducido_ruc.zip");
        properties.setProperty("output", "padron_reducido_ruc.zip");

        long execId = jobOperator.start("download_files", properties);
        Thread.sleep(sleepTime);

        Assert.assertEquals("Didn't pass as expected", BatchStatus.COMPLETED, jobOperator.getJobExecution(execId).getBatchStatus());
        Assert.assertTrue(Paths.get("padron_reducido_ruc.zip").toFile().exists());
    }

}