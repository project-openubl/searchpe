package io.searchpe.support.io;

import java.io.IOException;

public interface ITxtBeanReader extends ITxtReader {

    <T> T read(Class<T> clazz, String... nameMapping) throws IOException;

    <T> T read(T bean, String... nameMapping) throws IOException;

    <T> T read(Class<T> clazz, String[] nameMapping, ColumnProcessor... processors) throws IOException;

    <T> T read(T bean, String[] nameMapping, ColumnProcessor... processors) throws IOException;

}
