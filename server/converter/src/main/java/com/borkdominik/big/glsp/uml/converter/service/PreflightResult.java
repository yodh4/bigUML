package com.borkdominik.big.glsp.uml.converter.service;

import com.borkdominik.big.glsp.uml.converter.cli.CliErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PreflightResult {
    private final boolean ok;
    private final CliErrorCode errorCode;
    private final String message;
    private final List<PreflightCheck> checks;

    public PreflightResult(
        boolean ok,
        CliErrorCode errorCode,
        String message,
        List<PreflightCheck> checks
    ) {
        this.ok = ok;
        this.errorCode = errorCode;
        this.message = message;
        this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks);
    }

    public static PreflightResult ok(List<PreflightCheck> checks) {
        return new PreflightResult(true, CliErrorCode.NONE, "Preflight checks passed", checks);
    }

    public static PreflightResult fail(CliErrorCode code, String message, List<PreflightCheck> checks) {
        return new PreflightResult(false, code, message, checks);
    }

    public boolean isOk() {
        return ok;
    }

    public CliErrorCode getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public List<PreflightCheck> getChecks() {
        return Collections.unmodifiableList(checks);
    }
}
