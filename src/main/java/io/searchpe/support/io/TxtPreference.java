package io.searchpe.support.io;

import io.searchpe.support.io.comment.CommentMatcher;

public final class TxtPreference {

    public static final TxtPreference STANDARD_PREFERENCE = new TxtPreference.Builder('"', ',', "\r\n").build();
    public static final TxtPreference EXCEL_PREFERENCE = new TxtPreference.Builder('"', ',', "\n").build();
    public static final TxtPreference EXCEL_NORTH_EUROPE_PREFERENCE = new TxtPreference.Builder('"', ';', "\n").build();
    public static final TxtPreference TAB_PREFERENCE = new TxtPreference.Builder('"', '\t', "\n").build();

    private final char quoteChar;
    private final int delimiterChar;
    private final String endOfLineSymbols;
    private final boolean surroundingSpacesNeedQuotes;
    private final boolean ignoreEmptyLines;
    private final CsvEncoder encoder;
    private final QuoteMode quoteMode;
    private final CommentMatcher commentMatcher;

    private TxtPreference(Builder builder) {
        this.quoteChar = builder.quoteChar;
        this.delimiterChar = builder.delimiterChar;
        this.endOfLineSymbols = builder.endOfLineSymbols;
        this.surroundingSpacesNeedQuotes = builder.surroundingSpacesNeedQuotes;
        this.ignoreEmptyLines = builder.ignoreEmptyLines;
        this.commentMatcher = builder.commentMatcher;
        this.encoder = builder.encoder;
        this.quoteMode = builder.quoteMode;
    }

    public int getDelimiterChar() {
        return delimiterChar;
    }

    public String getEndOfLineSymbols() {
        return endOfLineSymbols;
    }

    public int getQuoteChar() {
        return quoteChar;
    }

    public boolean isSurroundingSpacesNeedQuotes() {
        return surroundingSpacesNeedQuotes;
    }

    public boolean isIgnoreEmptyLines() {
        return ignoreEmptyLines;
    }

    public CsvEncoder getEncoder() {
        return encoder;
    }

    public QuoteMode getQuoteMode() {
        return quoteMode;
    }

    public CommentMatcher getCommentMatcher() {
        return commentMatcher;
    }

    public static class Builder {

        private final char quoteChar;

        private final int delimiterChar;

        private final String endOfLineSymbols;

        private boolean surroundingSpacesNeedQuotes = false;

        private boolean ignoreEmptyLines = true;

        private CsvEncoder encoder;

        private QuoteMode quoteMode;

        private CommentMatcher commentMatcher;

        public Builder(final TxtPreference preference) {
            this.quoteChar = preference.quoteChar;
            this.delimiterChar = preference.delimiterChar;
            this.endOfLineSymbols = preference.endOfLineSymbols;
            this.surroundingSpacesNeedQuotes = preference.surroundingSpacesNeedQuotes;
            this.ignoreEmptyLines = preference.ignoreEmptyLines;
            this.encoder = preference.encoder;
            this.quoteMode = preference.quoteMode;
            this.commentMatcher = preference.commentMatcher;
        }

        public Builder(final char quoteChar, final int delimiterChar, final String endOfLineSymbols) {
            if( quoteChar == delimiterChar ) {
                throw new IllegalArgumentException(String.format("quoteChar and delimiterChar should not be the same character: %c", quoteChar));
            } else if( endOfLineSymbols == null ) {
                throw new NullPointerException("endOfLineSymbols should not be null");
            }
            this.quoteChar = quoteChar;
            this.delimiterChar = delimiterChar;
            this.endOfLineSymbols = endOfLineSymbols;
        }

        public Builder surroundingSpacesNeedQuotes(final boolean surroundingSpacesNeedQuotes) {
            this.surroundingSpacesNeedQuotes = surroundingSpacesNeedQuotes;
            return this;
        }

        public Builder ignoreEmptyLines(final boolean ignoreEmptyLines) {
            this.ignoreEmptyLines = ignoreEmptyLines;
            return this;
        }

        public Builder skipComments(final CommentMatcher commentMatcher) {
            if( commentMatcher == null ) {
                throw new NullPointerException("commentMatcher should not be null");
            }
            this.commentMatcher = commentMatcher;
            return this;
        }

        public Builder useEncoder(final CsvEncoder encoder) {
            if( encoder == null ) {
                throw new NullPointerException("encoder should not be null");
            }
            this.encoder = encoder;
            return this;
        }

        public Builder useQuoteMode(final QuoteMode quoteMode) {
            if( quoteMode == null ) {
                throw new NullPointerException("quoteMode should not be null");
            }
            this.quoteMode = quoteMode;
            return this;
        }

        public TxtPreference build() {
            if( encoder == null ) {
                encoder = new DefaultCsvEncoder();
            }

            if( quoteMode == null ) {
                quoteMode = new NormalQuoteMode();
            }

            return new TxtPreference(this);
        }

    }
    
}
