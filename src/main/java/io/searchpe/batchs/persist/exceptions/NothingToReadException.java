package io.searchpe.batchs.persist.exceptions;

public class NothingToReadException extends Exception {

    public NothingToReadException(String message) {
        super(message);
    }

    public NothingToReadException(String message, Throwable e) {
        super(message, e);
    }
}
