package io.searchpe.support.io;

import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.ICsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public final class FastForwardTxtListReader extends AbstractTxtReader implements ICsvListReader {

    private final int startRowNumber;

    /**
     * Constructs a new <tt>CsvListReader</tt> with the supplied Reader and CSV preferences. Note that the
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
    public FastForwardTxtListReader(final Reader reader, final CsvPreference preferences, final int startRowNumber) {
        super(reader, preferences);
        this.startRowNumber = startRowNumber;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> read() throws IOException {
        fastForwardToStartRow();
        if( readRow() ) {
            return new ArrayList<String>(getColumns());
        }

        return null; // EOF
    }

    /**
     * {@inheritDoc}
     */
    public List<Object> read(final CellProcessor... processors) throws IOException {
        fastForwardToStartRow();
        if( processors == null ) {
            throw new NullPointerException("processors should not be null");
        }

        if( readRow() ) {
            return executeProcessors(processors);
        }

        return null; // EOF
    }

    /**
     * {@inheritDoc}
     */
    public List<Object> executeProcessors(final CellProcessor... processors) {
        return super.executeProcessors(new ArrayList<Object>(getColumns().size()), processors);
    }

    private void fastForwardToStartRow() throws IOException {
        while (getRowNumber() < this.startRowNumber) {
            readRow();
        }
    }

}
