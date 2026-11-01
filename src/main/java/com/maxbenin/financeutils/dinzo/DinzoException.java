package com.maxbenin.financeutils.dinzo;

public class DinzoException extends RuntimeException {
    public DinzoException(String message) {
        super(message);
    }

    public DinzoException(String message, Throwable cause) {
        super(message, cause);
    }
}
