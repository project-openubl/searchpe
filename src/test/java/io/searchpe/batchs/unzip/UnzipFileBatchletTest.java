package io.searchpe.batchs.unzip;

import io.searchpe.utils.FileUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
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

@Ignore
@RunWith(Arquillian.class)
public class UnzipFileBatchletTest {

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
        deployment.addClasses(UnzipFileBatchlet.class, FileUtils.class);

        deployment.addAsResource(projectDefaults, "/project-defaults.yml");
        deployment.addAsResource("persistence-test.xml", "META-INF/persistence.xml");
        deployment.addAsManifestResource(EmptyAsset.INSTANCE, "WEB-INF/beans.xml");
        deployment.addAsResource("batch-jobs/unzip_files.xml", "META-INF/batch-jobs/unzip_files.xml");
        deployment.addAsResource("padron_reducido_ruc.zip", "padron_reducido_ruc.zip");
        deployment.addAllDependencies();

        return deployment;
    }

    @Test
    public void testUnzipFile() throws Exception {
        URL url = getClass().getClassLoader().getResource("/padron_reducido_ruc.zip");
        Assert.assertNotNull(url);
        String path = url.getPath();

        Properties properties = new Properties();
        properties.setProperty("fileName", path);
        properties.setProperty("output", "padron_reducido_ruc.txt");

        long execId = jobOperator.start("unzip_files", properties);
        Thread.sleep(sleepTime);

        Assert.assertEquals("Didn't pass as expected", BatchStatus.COMPLETED, jobOperator.getJobExecution(execId).getBatchStatus());
        Assert.assertTrue(Paths.get("padron_reducido_ruc.txt").toFile().exists());
    }

}