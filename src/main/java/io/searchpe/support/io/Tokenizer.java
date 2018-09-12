package io.searchpe.support.io;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

public class Tokenizer extends AbstractTokenizer {

    public Tokenizer(final Reader reader, final TxtPreference preferences) {
    }

    @Override
    public int getLineNumber() {
        return 0;
    }

    @Override
    public String getUntokenizedRow() {
        return null;
    }

    @Override
    public boolean readColumns(List<String> columns) throws IOException {
        return false;
    }

    @Override
    public void close() throws IOException {

    }

}
