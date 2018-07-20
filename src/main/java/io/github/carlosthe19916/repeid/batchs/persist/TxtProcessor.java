package io.github.carlosthe19916.repeid.batchs.persist;

import io.github.carlosthe19916.repeid.instantiators.BeanInstantiator;
import io.github.carlosthe19916.repeid.instantiators.BeanInstantiatorFactory;
import io.github.carlosthe19916.repeid.model.Company;

import javax.annotation.PostConstruct;
import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.ItemProcessor;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.function.Function;

@Named
public class TxtProcessor implements ItemProcessor {

    @Inject
    @BatchProperty
    private String regex;

    @Inject
    @BatchProperty
    private String header;

    @Inject
    @BatchProperty
    private String[] headerColumns;

    private BeanInstantiator<Company> instantiator;

    @PostConstruct
    protected void postConstruct() {
        Function<String, String> mapper = getMapperFunction();
        instantiator = BeanInstantiatorFactory.txtInstantiator(Company.class, header, regex, mapper);
    }

    @Override
    public Object processItem(Object item) throws Exception {
        String line = (String) item;
        String[] columns = Arrays.stream(line.split(regex)).map(String::trim).toArray(String[]::new);
        return instantiator.create(columns);
    }

    private Function<String, String> getMapperFunction() {
        String[] split = header.split(regex);
        final String[] headers = Arrays.stream(split).map(String::trim).toArray(String[]::new);

        return alias -> {
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].equals(alias)) {
                    return headerColumns[i];
                }
            }
            return alias;
        };
    }
}
