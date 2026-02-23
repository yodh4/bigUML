package com.borkdominik.big.glsp.uml.converter.service;

public final class PreflightCheck {
    private final String id;
    private final boolean ok;
    private final String detail;

    public PreflightCheck(String id, boolean ok, String detail) {
        this.id = id;
        this.ok = ok;
        this.detail = detail;
    }

    public String getId() {
        return id;
    }

    public boolean isOk() {
        return ok;
    }

    public String getDetail() {
        return detail;
    }
}
