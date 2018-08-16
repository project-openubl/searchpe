package io.searchpe.batchs.persist.exceptions;

public class MoreThanOneFileToChooseException extends Exception {

    public MoreThanOneFileToChooseException(String message) {
        super(message);
    }

    public MoreThanOneFileToChooseException(String message, Throwable e) {
        super(message, e);
    }
}
