package com.borkdominik.big.glsp.uml.converter.report;

public final class RuleLog {
    private final String id;
    private final int matches;
    private final String status;
    private final String summary;

    public RuleLog(String id, int matches, String status, String summary) {
        this.id = id;
        this.matches = matches;
        this.status = status;
        this.summary = summary;
    }

    public String getId() {
        return id;
    }

    public int getMatches() {
        return matches;
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }
}
