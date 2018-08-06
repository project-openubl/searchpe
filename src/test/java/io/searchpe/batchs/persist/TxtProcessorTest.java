package io.searchpe.batchs.persist;

import io.searchpe.migration.FlywayHibernateIntegrator;
import io.searchpe.model.Company;
import io.searchpe.model.Version;
import io.searchpe.producers.ContainerEntityManager;
import io.searchpe.producers.EntityManagerProducer;
import io.searchpe.repository.VersionRepository;
import io.searchpe.repository.VersionRepositoryImpl;
import io.searchpe.services.VersionService;
import io.searchpe.services.VersionServiceImpl;
import io.searchpe.utils.FileUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
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
import java.util.Properties;

@Ignore
@RunWith(Arquillian.class)
public class TxtProcessorTest {

    private static final String TXT_FILE_NAME = "padron_reducido_ruc.txt";

    private int sleepTime = 3000;
    private JobOperator jobOperator;

    @Deployment
    public static Archive createDeployment() throws Exception {
        URL url = Thread.currentThread().getContextClassLoader().getResource("project-test-defaults.yml");
        Assert.assertNotNull(url);
        File projectDefaults = new File(url.toURI());

        JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);

        deployment.setContextRoot("/");
        deployment.addClasses(FileUtils.class);
        deployment.addPackages(true, TxtReader.class.getPackage());
        deployment.addClasses(VersionService.class, VersionServiceImpl.class);
        deployment.addClasses(VersionRepository.class, VersionRepositoryImpl.class);
        deployment.addClasses(Version.class, Company.class);

        deployment.addClasses(EntityManagerProducer.class, ContainerEntityManager.class);
        deployment.addClasses(FlywayHibernateIntegrator.class);
        deployment.addAsResource("db/migration/h2");
        deployment.addAsResource("META-INF/services/org.hibernate.integrator.spi.Integrator", "META-INF/services/org.hibernate.integrator.spi.Integrator");

        deployment.addAsResource(projectDefaults, "/project-defaults.yml");
        deployment.addAsResource("persistence-test.xml", "META-INF/persistence.xml");
        deployment.addAsManifestResource("beans.xml", "beans.xml");
        deployment.addAsWebInfResource("beans.xml", "beans.xml");

        deployment.addAsResource("batch-jobs/persist.xml", "META-INF/batch-jobs/persist.xml");

        deployment.addAsResource(TXT_FILE_NAME, TXT_FILE_NAME);

        deployment.addAllDependencies();

        return deployment;
    }

    @Before
    public void before() {
        jobOperator = BatchRuntime.getJobOperator();
    }

    @Test
    public void testPersistData() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("sunatTxtCharset", "ISO-8859-1");
        properties.setProperty("sunatTxtRowSkips", String.valueOf(1L));
        properties.setProperty("sunatUnzipFileName", TXT_FILE_NAME);
        properties.setProperty("sunatTxtColumnSplitRegex", "\\|");
        properties.setProperty("sunatTxtHeadersTemplate", "RUC|NOMBRE O RAZÓN SOCIAL|ESTADO DEL CONTRIBUYENTE|CONDICIÓN DE DOMICILIO|UBIGEO|TIPO DE VÍA|NOMBRE DE VÍA|CÓDIGO DE ZONA|TIPO DE ZONA|NÚMERO|INTERIOR|LOTE|DEPARTAMENTO|MANZANA|KILÓMETRO");
        properties.setProperty("sunatModelHeadersTemplate", "ruc,razonSocial,estadoContribuyente,condicionDomicilio,ubigeo,tipoVia,nombreVia,codigoZona,tipoZona,numero,interior,lote,departamento,manzana,kilometro");

        long execId = jobOperator.start("persist", properties);
        Thread.sleep(sleepTime);

        Assert.assertEquals("Didn't pass as expected", BatchStatus.COMPLETED, jobOperator.getJobExecution(execId).getBatchStatus());
    }
}