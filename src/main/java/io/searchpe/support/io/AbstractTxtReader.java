package io.searchpe.support.io;

import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.exception.SuperCsvConstraintViolationException;
import org.supercsv.exception.SuperCsvException;
import org.supercsv.io.ICsvReader;
import org.supercsv.io.ITokenizer;
import org.supercsv.io.Tokenizer;
import org.supercsv.prefs.CsvPreference;
import org.supercsv.util.Util;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractTxtReader implements ICsvReader {

    private final org.supercsv.io.ITokenizer tokenizer;

    private final CsvPreference preferences;

    // the current tokenized columns
    private final List<String> columns = new ArrayList<String>();

    // the number of CSV records read
    private int rowNumber = 0;

    /**
     * Constructs a new <tt>AbstractCsvReader</tt>, using the default {@link org.supercsv.io.Tokenizer}.
     *
     * @param reader
     *            the reader
     * @param preferences
     *            the CSV preferences
     * @throws NullPointerException
     *             if reader or preferences are null
     */
    public AbstractTxtReader(final Reader reader, final CsvPreference preferences) {
        if( reader == null ) {
            throw new NullPointerException("reader should not be null");
        } else if( preferences == null ) {
            throw new NullPointerException("preferences should not be null");
        }

        this.preferences = preferences;
        this.tokenizer = new org.supercsv.io.Tokenizer(reader, preferences);
    }

    /**
     * Constructs a new <tt>AbstractCsvReader</tt>, using a custom {@link Tokenizer} (which should have already been set
     * up with the Reader, CsvPreference, and CsvContext). This constructor should only be used if the default Tokenizer
     * doesn't provide the required functionality.
     *
     * @param tokenizer
     *            the tokenizer
     * @param preferences
     *            the CSV preferences
     * @throws NullPointerException
     *             if tokenizer or preferences are null
     */
    public AbstractTxtReader(final ITokenizer tokenizer, final CsvPreference preferences) {
        if( tokenizer == null ) {
            throw new NullPointerException("tokenizer should not be null");
        } else if( preferences == null ) {
            throw new NullPointerException("preferences should not be null");
        }

        this.preferences = preferences;
        this.tokenizer = tokenizer;
    }

    /**
     * Closes the Tokenizer and its associated Reader.
     */
    public void close() throws IOException {
        tokenizer.close();
    }

    /**
     * {@inheritDoc}
     */
    public String get(final int n) {
        return columns.get(n - 1); // column numbers start at 1
    }

    /**
     * {@inheritDoc}
     */
    public String[] getHeader(final boolean firstLineCheck) throws IOException {

        if( firstLineCheck && tokenizer.getLineNumber() != 0 ) {
            throw new SuperCsvException(String.format(
                    "CSV header must be fetched as the first read operation, but %d lines have already been read",
                    tokenizer.getLineNumber()));
        }

        if( readRow() ) {
            return columns.toArray(new String[columns.size()]);
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    public int getLineNumber() {
        return tokenizer.getLineNumber();
    }

    /**
     * {@inheritDoc}
     */
    public String getUntokenizedRow() {
        return tokenizer.getUntokenizedRow();
    }

    /**
     * {@inheritDoc}
     */
    public int getRowNumber() {
        return rowNumber;
    }

    /**
     * {@inheritDoc}
     */
    public int length() {
        return columns.size();
    }

    /**
     * Gets the tokenized columns.
     *
     * @return the tokenized columns
     */
    protected List<String> getColumns() {
        return columns;
    }

    /**
     * Gets the preferences.
     *
     * @return the preferences
     */
    protected CsvPreference getPreferences() {
        return preferences;
    }

    /**
     * Calls the tokenizer to read a CSV row. The columns can then be retrieved using {@link #getColumns()}.
     *
     * @return true if something was read, and false if EOF
     * @throws IOException
     *             when an IOException occurs
     * @throws SuperCsvException
     *             on errors in parsing the input
     */
    protected boolean readRow() throws IOException {
        if( tokenizer.readColumns(columns) ) {
            rowNumber++;
            return true;
        }
        return false;
    }

    /**
     * Executes the supplied cell processors on the last row of CSV that was read and populates the supplied List of
     * processed columns.
     *
     * @param processedColumns
     *            the List to populate with processed columns
     * @param processors
     *            the cell processors
     * @return the updated List
     * @throws NullPointerException
     *             if processedColumns or processors is null
     * @throws SuperCsvConstraintViolationException
     *             if a CellProcessor constraint failed
     * @throws SuperCsvException
     *             if the wrong number of processors are supplied, or CellProcessor execution failed
     */
    protected List<Object> executeProcessors(final List<Object> processedColumns, final CellProcessor[] processors) {
        Util.executeCellProcessors(processedColumns, getColumns(), processors, getLineNumber(), getRowNumber());
        return processedColumns;
    }

}
