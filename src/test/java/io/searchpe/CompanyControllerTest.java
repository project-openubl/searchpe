package io.searchpe;

import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.arquillian.DefaultDeployment;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Ignore
@DefaultDeployment
@RunWith(Arquillian.class)
public class CompanyControllerTest {

//    @Deployment
//    public static Archive createDeployment() throws Exception {
//        URL url = Thread.currentThread().getContextClassLoader().getResource("project-test-defaults.yml");
//        Assert.assertNotNull(url);
//        File projectDefaults = new File(url.toURI());
//
//        JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);
//
//        deployment.setContextRoot("/");
//        deployment.addClasses(Version.class, Company.class);
//        deployment.addClasses(CompanyController.class, CompanyService.class, CompanyRepository.class);
//        deployment.addClasses(CompanyControllerImpl.class, CompanyServiceImpl.class, CompanyRepositoryImpl.class);
//
//        deployment.addClasses(FlywayHibernateIntegrator.class);
//        deployment.addAsResource("db/migration/h2");
//        deployment.addAsResource("META-INF/services/org.hibernate.integrator.spi.Integrator", "META-INF/services/org.hibernate.integrator.spi.Integrator");
//
//        deployment.addAsResource(projectDefaults, "/project-defaults.yml");
//        deployment.addAsResource("persistence-test.xml", "META-INF/persistence.xml");
//        deployment.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
//        deployment.addAllDependencies();
//
//        return deployment;
//    }

    @Ignore
    @Test
    public void getCompanies() throws Exception {
    }

    @Test
    @RunAsClient
    public void shoudlNotFindCompany() throws Exception {
        String id = "0";

        Client client = ClientBuilder.newClient();
        WebTarget target = client.target("http://localhost:8080")
                .path("api").path("companies").path(id);

        Response response = target.request(MediaType.APPLICATION_JSON).get();
        Assert.assertEquals(404, response.getStatus());
    }

}