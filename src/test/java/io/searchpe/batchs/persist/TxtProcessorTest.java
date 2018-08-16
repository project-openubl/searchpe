package io.searchpe.batchs.persist;

import io.searchpe.model.Company;
import org.junit.Assert;
import org.junit.Test;

public class TxtProcessorTest {


    @Test
    public void emulateBatch() throws Exception {
        String regex = "\\|";
        String modelFields = "ruc,razonSocial,estadoContribuyente,condicionDomicilio,ubigeo,tipoVia,nombreVia,codigoZona,tipoZona,numero,interior,lote,departamento,manzana,kilometro";

        TxtProcessor txtProcessor = new TxtProcessor();
        txtProcessor.setRegex(regex);
        txtProcessor.setHeader("RUC|NOMBRE O RAZÓN SOCIAL|ESTADO DEL CONTRIBUYENTE|CONDICIÓN DE DOMICILIO|UBIGEO|TIPO DE VÍA|NOMBRE DE VÍA|CÓDIGO DE ZONA|TIPO DE ZONA|NÚMERO|INTERIOR|LOTE|DEPARTAMENTO|MANZANA|KILÓMETRO");
        txtProcessor.setHeaderColumns(modelFields.split(","));

        // Process
        Company company = (Company) txtProcessor.processItem("20272209325|PESCA PERU MOLLENDO S.A.|BAJA DEFINITIVA|HABIDO|150122|AV.|JOSE PARDO|-|-|601|1603|-|-|-|-|");
        Assert.assertNotNull(company);
    }


}