package io.github.carlosthe19916.repeid.instantiators;

import io.github.carlosthe19916.repeid.model.Company;
import org.junit.Assert;
import org.junit.Test;

public class BeanInstantiatorTest {

    @Test
    public void shoudlCreatePojo() throws IllegalAccessException, NoSuchFieldException, InstantiationException {
        BeanInstantiator<Company> instantiator = new BeanInstantiator<>(Company.class, new String[]{"ruc", "razonSocial", "ubigeo"});

        Object[] columns = {"10467793549", "Carlos Feria", "050101"};
        Company company = instantiator.create(columns);

        Assert.assertNotNull(company);
        Assert.assertEquals("10467793549", company.getRuc());
        Assert.assertEquals("Carlos Feria", company.getRazonSocial());
        Assert.assertEquals("050101", company.getUbigeo());
    }

}