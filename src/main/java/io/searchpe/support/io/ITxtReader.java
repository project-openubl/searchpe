package io.searchpe.support.io;

import java.io.Closeable;
import java.io.IOException;

public interface ITxtReader extends Closeable {

    /**
     * This method is used to get an optional header of the TXT file and move the file cursor to the first row
     * containing data (the second row from the top). The header can subsequently be used as the
     * <code>nameMapping</code> array for read operations.
     *
     * @param firstLineCheck if true, ensures that this method is only called when reading the first line (as that's where the
     *                       header is meant to be)
     * @return the array of header fields, or null if EOF is encountered
     * @throws IOException if an I/O exception occurs
     * @throws IOException if firstLineCheck == true and it's not the first line being read
     */
    String[] getHeader(boolean firstLineCheck) throws IOException;

    /**
     * Gets the current row number (i.e. the number of Txt records - including the header - that have been read). This
     * differs from the lineNumber, which is the number of real lines that have been read in the file. The first row is
     * row 1 (which is typically the header row).
     *
     * @return the current row number
     * @since 2.0.0
     */
    int getRowNumber();

}
