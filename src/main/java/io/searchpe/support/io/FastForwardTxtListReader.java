package io.searchpe.support.io;

import java.io.IOException;
import java.io.InputStreamReader;

public class FastForwardTxtListReader extends AbstractTxtReader implements ITxtListReader {

    public FastForwardTxtListReader(InputStreamReader r, TxtPreference txtPreference, int startRowNumber) {

    }

    @Override
    public String[] getHeader(boolean firstLineCheck) throws IOException {
        return new String[0];
    }

    @Override
    public int getRowNumber() {
        return 0;
    }

    @Override
    public void close() throws IOException {

    }
}
