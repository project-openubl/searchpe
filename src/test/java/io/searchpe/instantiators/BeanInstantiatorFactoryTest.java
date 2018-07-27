package io.searchpe.instantiators;

import io.searchpe.model.Company;
import org.junit.Assert;
import org.junit.Test;

import java.util.function.Function;

public class BeanInstantiatorFactoryTest {

    @Test
    public void shouldCreateHeadersFromText() throws IllegalAccessException, NoSuchFieldException, InstantiationException {
        BeanInstantiator<Company> instantiator1 = BeanInstantiatorFactory.txtInstantiator(Company.class, "ruc|razonSocial|ubigeo", "\\|");
        BeanInstantiator<Company> instantiator2 = BeanInstantiatorFactory.txtInstantiator(Company.class, "ruc | razonSocial | ubigeo", "\\|");

        Object[] columns = {"10467793549", "Carlos Feria", "050101"};

        Company company = instantiator1.create(columns);

        Assert.assertNotNull(company);
        Assert.assertEquals("10467793549", company.getRuc());
        Assert.assertEquals("Carlos Feria", company.getRazonSocial());
        Assert.assertEquals("050101", company.getUbigeo());

        company = instantiator2.create(columns);

        Assert.assertNotNull(company);
        Assert.assertEquals("10467793549", company.getRuc());
        Assert.assertEquals("Carlos Feria", company.getRazonSocial());
        Assert.assertEquals("050101", company.getUbigeo());
    }

    @Test
    public void shouldCreateHeadersFromTextAndMapHeader() throws IllegalAccessException, NoSuchFieldException, InstantiationException {
        Function<String, String> mapper = new Function<String, String>() {
            @Override
            public String apply(String s) {
                switch (s) {
                    case "Mi Ruc":
                        return "ruc";
                    case "Mi Razon Social":
                        return "razonSocial";
                    case "Mi Ubigeo":
                        return "ubigeo";
                }
                return null;
            }
        };

        BeanInstantiator<Company> instantiator = BeanInstantiatorFactory.txtInstantiator(Company.class, "Mi Ruc | Mi Razon Social | Mi Ubigeo", "\\|", mapper);

        Object[] columns = {"10467793549", "Carlos Feria", "050101"};
        Company company = instantiator.create(columns);

        Assert.assertNotNull(company);
        Assert.assertEquals("10467793549", company.getRuc());
        Assert.assertEquals("Carlos Feria", company.getRazonSocial());
        Assert.assertEquals("050101", company.getUbigeo());
    }

    @Test
    public void shouldCreateWithHeadersLengthAndNoWithColumnsLength() throws IllegalAccessException, NoSuchFieldException, InstantiationException {
        BeanInstantiator<Company> instantiator = BeanInstantiatorFactory.txtInstantiator(Company.class, "ruc|razonSocial|ubigeo", "\\|");

        Object[] columns = {"10467793549", "Carlos Feria", "050101", "Mi Tipo Via"};
        Company company = instantiator.create(columns);

        Assert.assertNotNull(company);
        Assert.assertEquals("10467793549", company.getRuc());
        Assert.assertEquals("Carlos Feria", company.getRazonSocial());
        Assert.assertEquals("050101", company.getUbigeo());
    }
}