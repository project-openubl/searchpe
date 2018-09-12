package io.searchpe.support.io;

import io.searchpe.support.io.exceptions.SuperTxtException;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractTxtReader implements ITxtReader {

    private final ITokenizer tokenizer;

    private final TxtPreference preferences;

    // the current tokenized columns
    private final List<String> columns = new ArrayList<String>();

    // the number of Txt records read
    private int rowNumber = 0;

    public AbstractTxtReader(final Reader reader, final TxtPreference preferences) {
        if (reader == null) {
            throw new NullPointerException("reader should not be null");
        } else if (preferences == null) {
            throw new NullPointerException("preferences should not be null");
        }

        this.preferences = preferences;
        this.tokenizer = new Tokenizer(reader, preferences);
    }

    public AbstractTxtReader(final ITokenizer tokenizer, final TxtPreference preferences) {
        if (tokenizer == null) {
            throw new NullPointerException("tokenizer should not be null");
        } else if (preferences == null) {
            throw new NullPointerException("preferences should not be null");
        }

        this.preferences = preferences;
        this.tokenizer = tokenizer;
    }

    public void close() throws IOException {
        tokenizer.close();
    }

    @Override
    public String get(int n) {
        return columns.get(n - 1); // column numbers start at 1
    }

    @Override
    public String[] getHeader(final boolean firstLineCheck) throws IOException {
        if (firstLineCheck && tokenizer.getLineNumber() != 0) {
            throw new SuperTxtException(String.format("CSV header must be fetched as the first read operation, but %d lines have already been read", tokenizer.getLineNumber()));
        }

        if (readRow()) {
            return columns.toArray(new String[columns.size()]);
        }

        return null;
    }

    @Override
    public int getLineNumber() {
        return tokenizer.getLineNumber();
    }

    @Override
    public String getUntokenizedRow() {
        return tokenizer.getUntokenizedRow();
    }

    @Override
    public int getRowNumber() {
        return rowNumber;
    }

    @Override
    public int length() {
        return columns.size();
    }

    protected List<String> getColumns() {
        return columns;
    }

    protected TxtPreference getPreferences() {
        return preferences;
    }

    protected boolean readRow() throws IOException {
        if (tokenizer.readColumns(columns)) {
            rowNumber++;
            return true;
        }
        return false;
    }

    protected List<Object> executeProcessors(final List<Object> processedColumns, final ColumnProcessor[] processors) {
        Util.executeCellProcessors(processedColumns, getColumns(), processors, getLineNumber(), getRowNumber());
        return processedColumns;
    }

}
