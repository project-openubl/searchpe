package io.searchpe.batchs.persist;

import io.searchpe.utils.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

@RunWith(MockitoJUnitRunner.class)
public class TxtProcessorTest {

    private static final String PADRON_REDUCIDO_RUC_URL = "http://www2.sunat.gob.pe/padron_reducido_ruc.zip";
    private static final String FILE_NAME_WITHOUT_EXTENSION = "padron_reducido_ruc_test";

    @Spy
    private TxtProcessor txtProcessor = new TxtProcessor();

    @Test
    public void shoudlCreateRealData() throws Exception {
        String zipFileName = FILE_NAME_WITHOUT_EXTENSION + ".zip";
        String txtFileName = FILE_NAME_WITHOUT_EXTENSION + ".txt";

        if (!new File(txtFileName).exists()) {
            if (!new File(zipFileName).exists()) {
                FileUtils.downloadFile(PADRON_REDUCIDO_RUC_URL, zipFileName);
            }
            FileUtils.unzipFile(zipFileName, txtFileName);
        }

        FileInputStream inputStream = new FileInputStream(new File(txtFileName));
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "ISO-8859-1");
        BufferedReader reader = new BufferedReader(inputStreamReader);

        Mockito.when(txtProcessor.getRegex()).thenReturn("\\|");
        Mockito.when(txtProcessor.getHeader()).thenReturn("RUC|NOMBRE O RAZÓN SOCIAL|ESTADO DEL CONTRIBUYENTE|CONDICIÓN DE DOMICILIO|UBIGEO|TIPO DE VÍA|NOMBRE DE VÍA|CÓDIGO DE ZONA|TIPO DE ZONA|NÚMERO|INTERIOR|LOTE|DEPARTAMENTO|MANZANA|KILÓMETRO");
        Mockito.when(txtProcessor.getHeaderColumns()).thenReturn("ruc,razonSocial,estadoContribuyente,condicionDomicilio,ubigeo,tipoVia,nombreVia,codigoZona,tipoZona,numero,interior,lote,departamento,manzana,kilometro".split(","));

        long a = 0;
        String line;
        Object object;
        while ((line = reader.readLine()) != null) {
            a++;
            object = txtProcessor.processItem(line);
            Assert.assertNotNull(object);
        }

        Assert.assertEquals(12_349_621L, a);
    }
}