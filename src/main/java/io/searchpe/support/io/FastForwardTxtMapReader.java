package io.searchpe.support.io;

import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.prefs.CsvPreference;
import org.supercsv.util.Util;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FastForwardTxtMapReader extends AbstractTxtReader implements ICsvMapReader {

    private final int startRowNumber;

    /**
     * Constructs a new <tt>CsvMapReader</tt> with the supplied Reader and CSV preferences. Note that the
     * <tt>reader</tt> will be wrapped in a <tt>BufferedReader</tt> before accessed.
     *
     * @param reader
     *            the reader
     * @param preferences
     *            the CSV preferences
     * @param startRowNumber the row number to start reading
     * @throws NullPointerException
     *             if reader or preferences are null
     */
    public FastForwardTxtMapReader(final Reader reader, final CsvPreference preferences, final int startRowNumber) {
        super(reader, preferences);
        this.startRowNumber = startRowNumber;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, String> read(final String... nameMapping) throws IOException {
        fastForwardToStartRow();
        if (nameMapping == null) {
            throw new NullPointerException("nameMapping should not be null");
        }

        if (readRow()) {
            final Map<String, String> destination = new HashMap<>();
            org.supercsv.util.Util.filterListToMap(destination, nameMapping, getColumns());
            return destination;
        }

        return null; // EOF
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> read(final String[] nameMapping, final CellProcessor[] processors) throws IOException {
        fastForwardToStartRow();
        if (nameMapping == null) {
            throw new NullPointerException("nameMapping should not be null");
        } else if (processors == null) {
            throw new NullPointerException("processors should not be null");
        }

        if (readRow()) {
            // process the columns
            final List<Object> processedColumns = executeProcessors(new ArrayList<Object>(getColumns().size()), processors);

            // convert the List to a Map
            final Map<String, Object> destination = new HashMap<String, Object>(processedColumns.size());
            Util.filterListToMap((Map<String, Object>) destination, nameMapping, (List<Object>) processedColumns);
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
