package com.borkdominik.big.glsp.uml.converter.report;

public final class NamespaceDecision {
    private final String currentValue;
    private final String selectedValue;
    private final String decisionBasis;
    private final boolean ambiguous;

    public NamespaceDecision(String currentValue, String selectedValue, String decisionBasis, boolean ambiguous) {
        this.currentValue = currentValue;
        this.selectedValue = selectedValue;
        this.decisionBasis = decisionBasis;
        this.ambiguous = ambiguous;
    }

    public static NamespaceDecision empty() {
        return new NamespaceDecision(null, null, null, false);
    }

    public String getCurrentValue() {
        return currentValue;
    }

    public String getSelectedValue() {
        return selectedValue;
    }

    public String getDecisionBasis() {
        return decisionBasis;
    }

    public boolean isAmbiguous() {
        return ambiguous;
    }
}
