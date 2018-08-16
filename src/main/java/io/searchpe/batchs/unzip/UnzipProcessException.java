package io.searchpe.batchs.unzip;

public class UnzipProcessException extends Exception {

    public UnzipProcessException(String message) {
        super(message);
    }

    public UnzipProcessException(String message, Throwable e) {
        super(message, e);
    }
}
