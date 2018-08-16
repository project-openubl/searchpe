package io.searchpe.batchs.persist;

import io.searchpe.instantiators.BeanInstantiator;
import io.searchpe.instantiators.BeanInstantiatorFactory;
import io.searchpe.model.Company;

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

    @Override
    public Object processItem(Object item) throws Exception {
        if (instantiator == null) {
            instantiator = BeanInstantiatorFactory.txtInstantiator(Company.class, getHeader(), getRegex(), getMapperFunction());
        }

        String line = (String) item;
        String[] columns = Arrays.stream(line.split(getRegex())).map(String::trim).toArray(String[]::new);
        return instantiator.create(columns);
    }

    private Function<String, String> getMapperFunction() {
        String[] split = getHeader().split(getRegex());
        final String[] headers = Arrays.stream(split).map(String::trim).toArray(String[]::new);

        return alias -> {
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].equals(alias)) {
                    return getHeaderColumns()[i];
                }
            }
            return alias;
        };
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String[] getHeaderColumns() {
        return headerColumns;
    }

    public void setHeaderColumns(String[] headerColumns) {
        this.headerColumns = headerColumns;
    }
}
