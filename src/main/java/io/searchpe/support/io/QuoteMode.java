package io.searchpe.support.io;

public interface QuoteMode {

    /**
     * Determines whether surrounding quotes are mandatory in cases where the CSV column would not normally be quoted
     * (the data to be written doesn't contain special characters).
     *
     * @param csvColumn
     *            an element of a CSV file
     * @param context
     *            the context
     * @param preference
     *            the CSV preferences
     * @return true if surrounding quotes are mandatory, otherwise false
     */
    boolean quotesRequired(String csvColumn, TxtContext context, TxtPreference preference);

}
