package io.searchpe.support.io;

import io.searchpe.support.io.comment.CommentMatcher;
import io.searchpe.support.io.comment.CommentMatches;
import io.searchpe.support.io.comment.CommentStartsWith;
import org.jberet.support._private.SupportMessages;
import org.jberet.support.io.*;

import javax.batch.api.BatchProperty;
import javax.batch.operations.BatchRuntimeException;
import javax.inject.Inject;
import java.lang.reflect.Constructor;

import static io.searchpe.support.io.TxtProperties.*;

/*
 * @see <a href="http://supercsv.sourceforge.net/preferences.html">CSV Preferences</a>
 */
public abstract class TxtItemReaderWriterBase extends ItemReaderWriterBase {

    static final Class[] stringParameterTypes = {String.class};
    static final ColumnProcessor[] noCellProcessors = new ColumnProcessor[0];

    @Inject
    @BatchProperty
    protected String[] nameMapping;

    @Inject
    @BatchProperty
    protected Class beanType;

    @Inject
    @BatchProperty
    protected String preference;

    @Inject
    @BatchProperty
    protected String quoteChar;

    @Inject
    @BatchProperty
    protected String delimiterChar;

    @Inject
    @BatchProperty
    protected String endOfLineSymbols;

    @Inject
    @BatchProperty
    protected String surroundingSpacesNeedQuotes;

    @Inject
    @BatchProperty
    protected String commentMatcher;

    @Inject
    @BatchProperty
    protected String encoder;

    @Inject
    @BatchProperty
    protected String quoteMode;

    @Inject
    @BatchProperty
    protected String cellProcessors;

    @Inject
    @BatchProperty
    protected String charset;

    protected ColumnProcessor[] cellProcessorInstances;

    protected TxtPreference getTxtPreference() {
        TxtPreference txtPreference;
        if (preference == null || STANDARD_PREFERENCE.equals(preference)) {
            txtPreference = TxtPreference.STANDARD_PREFERENCE;
        } else if (EXCEL_PREFERENCE.equals(preference)) {
            txtPreference = TxtPreference.EXCEL_PREFERENCE;
        } else if (EXCEL_NORTH_EUROPE_PREFERENCE.equals(preference)) {
            txtPreference = TxtPreference.EXCEL_NORTH_EUROPE_PREFERENCE;
        } else if (TAB_PREFERENCE.equals(preference)) {
            txtPreference = TxtPreference.TAB_PREFERENCE;
        } else {
            throw new BatchRuntimeException(String.format("Invalid reader or writer property value %s for key %s", preference, PREFERENCE_KEY));
        }

        //do not trim quoteChar or delimiterChar. They can be tab (\t) and after trim, it will be just empty
        if (quoteChar != null || delimiterChar != null || endOfLineSymbols != null || surroundingSpacesNeedQuotes != null || commentMatcher != null || encoder != null || quoteMode != null) {
            final TxtPreference.Builder builder = new TxtPreference.Builder(
                    quoteChar == null ? (char) txtPreference.getQuoteChar() : quoteChar.charAt(0),
                    delimiterChar == null ? txtPreference.getDelimiterChar() : (int) delimiterChar.charAt(0),
                    endOfLineSymbols == null ? txtPreference.getEndOfLineSymbols() : endOfLineSymbols.trim()
            );
            if (surroundingSpacesNeedQuotes != null) {
                builder.surroundingSpacesNeedQuotes(Boolean.parseBoolean(surroundingSpacesNeedQuotes.trim()));
            }
            if (commentMatcher != null) {
                builder.skipComments(getCommentMatcher(commentMatcher));
            }
            if (encoder != null) {
                final CsvEncoder encoder1 = getEncoder(encoder);
                if (encoder1 != null) {
                    builder.useEncoder(encoder1);
                }
            }
            if (quoteMode != null) {
                final QuoteMode quoteMode1 = getQuoteMode(quoteMode);
                if (quoteMode1 != null) {
                    builder.useQuoteMode(quoteMode1);
                }
            }
            txtPreference = builder.build();
        }

        return txtPreference;
    }

    /**
     * Gets the cell processors for reading CSV resource.  The default implementation returns an empty array,
     * and subclasses may override it to provide more meaningful cell processors.
     *
     * @return an array of cell processors
     */
    protected ColumnProcessor[] getCellProcessors() {
        if (this.cellProcessors == null) {
            return TxtItemReaderWriterBase.noCellProcessors;
        }
        return CellProcessorConfig.parseCellProcessors(this.cellProcessors.trim());
    }

    /**
     * Gets the field names of the target bean, if they differ from the CSV header, or if there is no CSV header.
     *
     * @return an string array of field names of the target bean. Return null if CSV header exactly match the bean
     * field.
     */
    protected String[] getNameMapping() {
        return this.nameMapping;
    }

    /**
     * Gets the configured {@code org.supercsv.quote.QuoteMode}.
     *
     * @param val property value of quoteMode property in this batch artifact. For example,
     *            <p>
     *            <ul>
     *            <li>default
     *            <li>always
     *            <li>select 1, 2, 3
     *            <li>select true, true, false
     *            <li>column 1, 2, 3
     *            <li>column true, true, false
     *            <li>my.own.MyQuoteMode
     *            </ul>
     * @return a QuoteMode
     */
    protected QuoteMode getQuoteMode(final String val) {
        final String[] parts = val.split("[,\\s]+");
        final String quoteModeName;
        if (parts.length == 1) {
            //there is only 1 chunk, either default, always, or custom encoder
            quoteModeName = parts[0];
            if (quoteModeName.equalsIgnoreCase(DEFAULT)) {
                return null;
            } else if (quoteModeName.equalsIgnoreCase(ALWAYS)) {
                return new AlwaysQuoteMode();
            } else {
                return loadAndInstantiate(quoteModeName, val, null);
            }
        } else {
            quoteModeName = parts[0];
            final String encoderNameLowerCase = quoteModeName.toLowerCase();
            if (encoderNameLowerCase.startsWith(SELECT) || encoderNameLowerCase.startsWith(COLUMN)) {
                try {
                    Integer.parseInt(parts[1]);
                    return new ColumnQuoteMode(convertToIntParams(parts, 1, parts.length - 1));
                } catch (final NumberFormatException e) {
                    return new ColumnQuoteMode(convertToBooleanParams(parts));
                }
            } else {
                throw SupportMessages.MESSAGES.invalidReaderWriterProperty(null, val, ENCODER_KEY);
            }
        }
    }

    /**
     * Gets the configured {@code org.supercsv.encoder.CsvEncoder}.
     *
     * @param val property value of encoder property in this batch artifact. For example,
     *            <p>
     *            <ul>
     *            <li>default
     *            <li>select 1, 2, 3
     *            <li>select true, true, false
     *            <li>column 1, 2, 3
     *            <li>column true, true, false
     *            <li>my.own.MyCsvEncoder
     *            </ul>
     * @return a {@code CsvEncoder}
     */
    protected CsvEncoder getEncoder(final String val) {
        final String[] parts = val.split("[,\\s]+");
        final String encoderName;
        if (parts.length == 1) {
            //there is only 1 chunk, either default, or custom encoder
            encoderName = parts[0];
            if (encoderName.equalsIgnoreCase(DEFAULT)) {
                return null;
            } else {
                return loadAndInstantiate(encoderName, val, null);
            }
        } else {
            encoderName = parts[0];
            final String encoderNameLowerCase = encoderName.toLowerCase();
            if (encoderNameLowerCase.startsWith(SELECT) || encoderNameLowerCase.startsWith(COLUMN)) {
                try {
                    Integer.parseInt(parts[1]);
                    return new SelectiveCsvEncoder(convertToIntParams(parts, 1, parts.length - 1));
                } catch (final NumberFormatException e) {
                    return new SelectiveCsvEncoder(convertToBooleanParams(parts));
                }
            } else {
                throw SupportMessages.MESSAGES.invalidReaderWriterProperty(null, val, ENCODER_KEY);
            }
        }
    }

    /**
     * Gets the configured {@code io.searchpe.support.io.comment.CommentMatcher}.
     *
     * @param val property value of commentMatcher property in this batch artifact. For example,
     *            <p>
     *            <ul>
     *            <li>starts with '#'
     *            <li>startswith  '##'
     *            <li>startsWith  '##'
     *            <li>matches '#.*#'
     *            <li>my.own.MyCommentMatcher
     *            </ul>
     * @return a {@code CommentMatcher}
     */
    protected CommentMatcher getCommentMatcher(String val) {
        val = val.trim();
        final char paramQuoteChar = '\'';
        final int singleQuote1 = val.indexOf(paramQuoteChar);
        String matcherName = null;
        String matcherParam = null;
        if (singleQuote1 < 0) {
            final String[] parts = val.split("\\s");
            if (parts.length == 1) {
                //there is only 1 chunk, assume it's the custom CommentMatcher type
                return loadAndInstantiate(parts[0], val, null);
            } else if (parts.length == 2) {
                matcherName = parts[0];
                matcherParam = parts[1];
            } else {
                throw SupportMessages.MESSAGES.missingQuote(val);
            }
        }
        if (matcherName == null) {
            matcherName = val.substring(0, singleQuote1 - 1).trim();
            final int paramQuoteCharEnd = val.lastIndexOf(paramQuoteChar);
            matcherParam = val.substring(singleQuote1 + 1, paramQuoteCharEnd);
            matcherName = matcherName.split("\\s")[0];
        }

        final CommentMatcher commentMatcher;
        if (matcherName.equalsIgnoreCase(STARTS_WITH) || matcherName.equalsIgnoreCase(STARTS_WITH_FUZZY) || matcherName.equalsIgnoreCase(STARTS_WITH_FUZZY2)) {
            commentMatcher = new CommentStartsWith(matcherParam);
        } else if (matcherName.equalsIgnoreCase(MATCHES) || matcherName.equalsIgnoreCase(MATCHES_FUZZY)) {
            commentMatcher = new CommentMatches(matcherParam);
        } else {
            throw SupportMessages.MESSAGES.invalidReaderWriterProperty(null, val, COMMENT_MATCHER_KEY);
        }

        return commentMatcher;
    }

    private static <T> T loadAndInstantiate(final String className, final String contextVal, final String param) {
        try {
            final Class<?> aClass = TxtItemReaderWriterBase.class.getClassLoader().loadClass(className);
            if (param == null) {
                return (T) aClass.newInstance();
            } else {
                final Constructor<?> constructor = aClass.getConstructor(TxtItemReaderWriterBase.stringParameterTypes);
                return (T) constructor.newInstance(param);
            }
        } catch (final Exception e) {
            throw SupportMessages.MESSAGES.failToLoadOrCreateCustomType(e, contextVal);
        }
    }

    static int[] convertToIntParams(final String[] strings, final int start, final int count) {
        final int[] ints = new int[count];
        for (int i = start, j = 0; j < count && i < strings.length; i++, j++) {
            ints[j] = Integer.parseInt(strings[i]);
        }
        return ints;
    }

    private static boolean[] convertToBooleanParams(final String[] strings) {
        final boolean[] booleans = new boolean[strings.length - 1];
        for (int i = 1; i < strings.length; i++) {
            booleans[i - 1] = Boolean.parseBoolean(strings[i]);
        }
        return booleans;
    }
}
