package io.searchpe.support.io;

import java.io.Closeable;
import java.io.IOException;

public interface ITxtReader extends Closeable {

    /**
     * Get column N of the current line (column indexes begin at 1).
     *
     * @param n
     *            the index of the column to get
     * @return the n'th column
     * @throws IndexOutOfBoundsException
     *             if the supplied index is not a valid column index
     * @since 1.0
     */
    String get(int n);

    /**
     * This method is used to get an optional header of the CSV file and move the file cursor to the first row
     * containing data (the second row from the top). The header can subsequently be used as the
     * <code>nameMapping</code> array for read operations.
     *
     * @param firstLineCheck
     *            if true, ensures that this method is only called when reading the first line (as that's where the
     *            header is meant to be)
     * @return the array of header fields, or null if EOF is encountered
     * @throws IOException
     *             if an I/O exception occurs
     * @throws IOException
     *             if firstLineCheck == true and it's not the first line being read
     * @since 1.0
     */
    String[] getHeader(boolean firstLineCheck) throws IOException;

    /**
     * Gets the current position in the file, where the first line of the file is line number 1.
     *
     * @return the line number
     * @since 1.0
     */
    int getLineNumber();

    /**
     * Returns the untokenized CSV row that was just read (which can potentially span multiple lines in the file).
     *
     * @return the untokenized CSV row that was just read
     * @since 2.0.0
     */
    String getUntokenizedRow();

    /**
     * Gets the current row number (i.e. the number of CSV records - including the header - that have been read). This
     * differs from the lineNumber, which is the number of real lines that have been read in the file. The first row is
     * row 1 (which is typically the header row).
     *
     * @return the current row number
     * @since 2.0.0
     */
    int getRowNumber();

    /**
     * Returns the length (i.e. number of columns) of the current row.
     *
     * @return the length of the current row
     * @since 1.0
     */
    int length();

}
