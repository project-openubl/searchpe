package io.searchpe.support.io;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FastForwardTxtMapReader extends AbstractTxtReader implements ITxtMapReader {

    private final int startRowNumber;

    public FastForwardTxtMapReader(final Reader reader, final TxtPreference preferences, final int startRowNumber) {
        super(reader, preferences);
        this.startRowNumber = startRowNumber;
    }

    @Override
    public Map<String, String> read(String... nameMapping) throws IOException {
        fastForwardToStartRow();
        if (nameMapping == null) {
            throw new NullPointerException("nameMapping should not be null");
        }

        if (readRow()) {
            final Map<String, String> destination = new HashMap<>();
            Util.filterListToMap(destination, nameMapping, getColumns());
            return destination;
        }

        return null; // EOF
    }

    @Override
    public Map<String, Object> read(final String[] nameMapping, final ColumnProcessor[] processors) throws IOException {
        fastForwardToStartRow();
        if (nameMapping == null) {
            throw new NullPointerException("nameMapping should not be null");
        } else if (processors == null) {
            throw new NullPointerException("processors should not be null");
        }

        if (readRow()) {
            // process the columns
            final List<Object> processedColumns = executeProcessors(new ArrayList<>(getColumns().size()), processors);

            // convert the List to a Map
            final Map<String, Object> destination = new HashMap<>(processedColumns.size());
            Util.filterListToMap(destination, nameMapping, processedColumns);
            return destination;
        }

        return null; // EOF
    }

    private void fastForwardToStartRow() throws IOException {
        while (getRowNumber() < this.startRowNumber) {
            readRow();
        }
    }

}
