package com.borkdominik.big.glsp.uml.converter.service;

import com.borkdominik.big.glsp.uml.converter.report.NamespaceDecision;
import com.borkdominik.big.glsp.uml.converter.report.ProfileMetadata;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class NamespaceDecisionResolver {
    public static final String UMLVMPROFILE_PREFIX = "umlvmprofile";

    public NamespaceDecision resolve(Document document, ProfileMetadata metadata) {
        if (document == null) {
            return new NamespaceDecision(null, null, "document-missing", true);
        }
        Element root = document.getDocumentElement();
        if (root == null) {
            return new NamespaceDecision(null, null, "root-missing", true);
        }

        String current = root.getAttribute("xmlns:" + UMLVMPROFILE_PREFIX);
        String canonicalNsUri = metadata == null ? null : metadata.getEpackageNsUri();

        if (canonicalNsUri == null || canonicalNsUri.isBlank()) {
            return new NamespaceDecision(current, current, "canonical-nsuri-missing", true);
        }

        if (current != null && !current.isBlank() && current.equals(canonicalNsUri)) {
            return new NamespaceDecision(current, canonicalNsUri, "input-namespace-matches-canonical-nsuri", false);
        }

        if (current == null || current.isBlank()) {
            return new NamespaceDecision(current, canonicalNsUri, "input-namespace-missing", true);
        }

        return new NamespaceDecision(current, canonicalNsUri, "input-namespace-unexpected", true);
    }
}
