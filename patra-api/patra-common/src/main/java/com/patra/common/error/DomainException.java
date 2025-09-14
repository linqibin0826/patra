package com.patra.common.error;

public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }

    public static DomainException illegalState(String message) {
        return new DomainException(message);
    }

    public static DomainException illegalArg(String message) {
        return new DomainException(message);
    }
}
