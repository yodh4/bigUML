package com.borkdominik.big.glsp.uml.converter.cli;

public final class CliException extends RuntimeException {
    private final CliErrorCode errorCode;

    public CliException(CliErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CliErrorCode getErrorCode() {
        return errorCode;
    }
}
