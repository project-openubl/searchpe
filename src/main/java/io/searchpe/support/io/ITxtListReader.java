package io.searchpe.support.io;

import java.io.IOException;
import java.util.List;

public interface ITxtListReader {

    List<String> read() throws IOException;

    List<Object> read(ColumnProcessor... processors) throws IOException;

}
