package io.searchpe.support.io;

import org.supercsv.prefs.CsvPreference;

public class TxtPreference {

    /**
     * Ready to use configuration that should cover 99% of all usages.
     */
    public static final CsvPreference TXT_PREFERENCE = new CsvPreference.Builder('"', ',', "\r\n").build();

}
