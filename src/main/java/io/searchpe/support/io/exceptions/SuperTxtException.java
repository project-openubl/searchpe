package io.searchpe.support.io.exceptions;

import io.searchpe.support.io.TxtContext;

public class SuperTxtException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private TxtContext csvContext;

    public SuperTxtException(final String msg) {
        super(msg);
    }

    public SuperTxtException(final String msg, final TxtContext context) {
        super(msg);
        this.csvContext = context;
    }

}
