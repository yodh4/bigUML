package com.borkdominik.big.glsp.uml.converter.service;

import com.borkdominik.big.glsp.uml.converter.cli.CliErrorCode;

public final class ProfileMetadataException extends Exception {
    private final CliErrorCode errorCode;

    public ProfileMetadataException(CliErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CliErrorCode getErrorCode() {
        return errorCode;
    }
}
