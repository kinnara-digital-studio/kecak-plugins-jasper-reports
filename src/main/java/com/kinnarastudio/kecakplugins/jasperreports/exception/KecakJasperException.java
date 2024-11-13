package com.kinnarastudio.kecakplugins.jasperreports.exception;

public class KecakJasperException extends Exception {
    public KecakJasperException(Throwable cause) {
        super(cause);
    }

    public KecakJasperException(String message) {
        super(message);
    }

    public KecakJasperException(String message, Throwable cause) {
        super(message, cause);
    }
}
