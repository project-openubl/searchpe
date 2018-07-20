package io.github.carlosthe19916.repeid.batchs;

import io.github.carlosthe19916.repeid.model.Company;
import io.github.carlosthe19916.repeid.instantiators.BeanInstantiator;
import io.github.carlosthe19916.repeid.instantiators.BeanInstantiatorFactory;

import javax.annotation.PostConstruct;
import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.ItemProcessor;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.function.Function;

@Named
public class TxtProcessor implements ItemProcessor {

    private static final Function<String, String> MAPPER = TxtProcessor::apply;

    @Inject
    @BatchProperty
    private String header;

    @Inject
    @BatchProperty
    private String regex;

    private BeanInstantiator<Company> instantiator;

    @PostConstruct
    protected void postConstruct() {
        instantiator = BeanInstantiatorFactory.txtInstantiator(Company.class, header, regex, MAPPER);
    }

    @Override
    public Object processItem(Object item) throws Exception {
        String line = (String) item;
        String[] columns = Arrays.stream(line.split(regex)).map(String::trim).toArray(String[]::new);
        return instantiator.create(columns);
    }

    private static String apply(String alias) {
        switch (alias) {
            case "RUC":
                return "ruc";
            case "NOMBRE O RAZÓN SOCIAL":
                return "razonSocial";
            case "ESTADO DEL CONTRIBUYENTE":
                return "estadoContribuyente";
            case "CONDICIÓN DE DOMICILIO":
                return "condicionDomicilio";
            case "UBIGEO":
                return "ubigeo";
            case "TIPO DE VÍA":
                return "tipoVia";
            case "NOMBRE DE VÍA":
                return "nombreVia";
            case "CÓDIGO DE ZONA":
                return "codigoZona";
            case "TIPO DE ZONA":
                return "tipoZona";
            case "NÚMERO":
                return "numero";
            case "INTERIOR":
                return "interior";
            case "LOTE":
                return "lote";
            case "DEPARTAMENTO":
                return "departamento";
            case "MANZANA":
                return "manzana";
            case "KILÓMETRO":
                return "kilometro";
            default:
                return alias;
        }
    }
}
