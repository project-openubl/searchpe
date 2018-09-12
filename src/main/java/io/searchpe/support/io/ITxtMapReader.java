package io.searchpe.support.io;

import java.io.IOException;
import java.util.Map;

public interface ITxtMapReader {

    Map<String, String> read(String... nameMapping) throws IOException;

    Map<String, Object> read(String[] nameMapping, ColumnProcessor[] processors) throws IOException;

}
