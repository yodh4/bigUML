package com.borkdominik.big.glsp.uml.converter.service;

import com.borkdominik.big.glsp.uml.converter.report.NamespaceDecision;
import com.borkdominik.big.glsp.uml.converter.report.ProfileMetadata;
import com.borkdominik.big.glsp.uml.converter.report.RuleLog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class TransformEngine {
    private static final String PROFILE_APPLICATION = "profileApplication";
    private static final String EANNOTATIONS = "eAnnotations";
    private static final String REFERENCES = "references";
    private static final String APPLIED_PROFILE = "appliedProfile";
    private static final String UML_MODEL_TAG = "uml:Model";
    private static final String UML_ANNOTATION_SOURCE = "http://www.eclipse.org/uml2/2.0.0/UML";
    private static final String PROFILE_FILENAME = "uml-vm-profile.profile.uml";

    public TransformResult apply(Document document, ProfileMetadata metadata, NamespaceDecision namespaceDecision) throws TransformException {
        if (document == null || metadata == null) {
            throw new TransformException("Document or metadata missing");
        }

        Set<String> originalIds = captureXmiIds(document);

        List<RuleLog> rules = new ArrayList<>();

        ProfileApplicationSelection selection = locateProfileApplication(document);
        if (selection == null || selection.retainedProfileApplication == null) {
            throw new TransformException("No profileApplication nodes found");
        }

        int cr01Matches = rewriteProfileHrefs(selection.retainedProfileApplication, PROFILE_FILENAME);
        rules.add(new RuleLog("CR-01", cr01Matches, "applied", "Rewrote profile hrefs to relative"));

        int cr02Matches = setAppliedProfileFragment(selection.retainedProfileApplication, metadata.getProfileRootId());
        rules.add(new RuleLog("CR-02", cr02Matches, "applied", "Set appliedProfile fragment"));

        int cr03Matches = setReferencesFragment(selection.retainedProfileApplication, metadata.getEpackageId());
        rules.add(new RuleLog("CR-03", cr03Matches, "applied", "Set references fragment"));

        RuleLog cr04Log = deduplicateProfileApplications(selection);
        rules.add(cr04Log);

        int cr05Matches = setNamespace(document, namespaceDecision.getSelectedValue());
        rules.add(new RuleLog("CR-05", cr05Matches, "applied", "Set xmlns:umlvmprofile"));

        int cr07Matches = setSchemaLocation(document, namespaceDecision.getSelectedValue(), metadata.getEpackageId());
        rules.add(new RuleLog("CR-07", cr07Matches, "applied", "Set xsi:schemaLocation mapping"));

        List<String> idDrift = detectIdDrift(originalIds, captureXmiIds(document), selection.allowedRemovedIds);
        if (!idDrift.isEmpty()) {
            rules.add(new RuleLog("CR-06", idDrift.size(), "failed", "xmi:id drift detected"));
            throw new TransformException("xmi:id drift detected: " + String.join(", ", idDrift));
        }
        rules.add(new RuleLog("CR-06", 0, "applied", "Verified xmi:id preservation"));

        List<String> rulesApplied = new ArrayList<>();
        rulesApplied.add("CR-01");
        rulesApplied.add("CR-02");
        rulesApplied.add("CR-03");
        rulesApplied.add("CR-04");
        rulesApplied.add("CR-05");
        rulesApplied.add("CR-06");
        rulesApplied.add("CR-07");

        return new TransformResult(document, rules, rulesApplied);
    }

    private Set<String> captureXmiIds(Document document) {
        Set<String> ids = new HashSet<>();
        NodeList all = document.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node node = all.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (element.hasAttribute("xmi:id")) {
                    ids.add(element.getAttribute("xmi:id"));
                }
            }
        }
        return ids;
    }

    private Set<String> captureXmiIds(Element element) {
        Set<String> ids = new HashSet<>();
        if (element == null) {
            return ids;
        }
        if (element.hasAttribute("xmi:id")) {
            ids.add(element.getAttribute("xmi:id"));
        }
        NodeList all = element.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node node = all.item(i);
            if (node instanceof Element) {
                Element child = (Element) node;
                if (child.hasAttribute("xmi:id")) {
                    ids.add(child.getAttribute("xmi:id"));
                }
            }
        }
        return ids;
    }

    private List<String> detectIdDrift(Set<String> before, Set<String> after, Set<String> allowedRemovals) {
        List<String> drift = new ArrayList<>();
        for (String id : before) {
            if (allowedRemovals != null && allowedRemovals.contains(id)) {
                continue;
            }
            if (!after.contains(id)) {
                drift.add(id);
            }
        }
        return drift;
    }

    private ProfileApplicationSelection locateProfileApplication(Document document) {
        List<Element> allProfileApplications = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName(PROFILE_APPLICATION);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                allProfileApplications.add((Element) node);
            }
        }

        if (allProfileApplications.isEmpty()) {
            return null;
        }

        Element model = findModelElement(document);
        Element retained = null;
        if (!allProfileApplications.isEmpty()) {
            for (Element candidate : allProfileApplications) {
                if (candidate.getParentNode() == model) {
                    retained = candidate;
                    break;
                }
            }
            if (retained == null) {
                retained = allProfileApplications.get(0);
                moveToModelEnd(model, retained);
            }
        }

        return new ProfileApplicationSelection(retained, allProfileApplications);
    }

    private RuleLog deduplicateProfileApplications(ProfileApplicationSelection selection) {
        int removedCount = 0;
        if (selection == null) {
            return new RuleLog("CR-04", 0, "skipped", "No profileApplication nodes");
        }
        int total = selection.allProfileApplications.size();
        for (Element candidate : selection.allProfileApplications) {
            if (candidate != selection.retainedProfileApplication && candidate.getParentNode() != null) {
                selection.allowedRemovedIds.addAll(captureXmiIds(candidate));
                candidate.getParentNode().removeChild(candidate);
                removedCount += 1;
            }
        }
        return new RuleLog(
            "CR-04",
            removedCount,
            "applied",
            "Deduplicated profileApplication nodes (kept 1 of " + total + ")"
        );
    }

    private Element findModelElement(Document document) {
        NodeList nodes = document.getElementsByTagName(UML_MODEL_TAG);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node instanceof Element ? (Element) node : null;
    }

    private void moveToModelEnd(Element model, Element profileApplication) {
        if (model == null || profileApplication == null) {
            return;
        }
        if (profileApplication.getParentNode() != null) {
            profileApplication.getParentNode().removeChild(profileApplication);
        }
        NodeList children = model.getChildNodes();
        Node insertAfter = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && "packagedElement".equals(child.getNodeName())) {
                insertAfter = child;
            }
        }
        if (insertAfter != null && insertAfter.getNextSibling() != null) {
            model.insertBefore(profileApplication, insertAfter.getNextSibling());
        } else {
            model.appendChild(profileApplication);
        }
    }

    private int rewriteProfileHrefs(Element profileApplication, String base) {
        if (profileApplication == null) {
            return 0;
        }
        int matches = 0;
        NodeList references = profileApplication.getElementsByTagName(REFERENCES);
        for (int i = 0; i < references.getLength(); i++) {
            Element ref = (Element) references.item(i);
            if (!isUmlAnnotation(ref.getParentNode())) {
                continue;
            }
            if (ref.hasAttribute("href")) {
                String value = ref.getAttribute("href");
                if (value.contains("uml-vm-profile.profile.uml")) {
                    String fragment = extractFragment(value);
                    ref.setAttribute("href", base + fragment);
                    matches += 1;
                }
            }
        }

        NodeList appliedProfiles = profileApplication.getElementsByTagName(APPLIED_PROFILE);
        for (int i = 0; i < appliedProfiles.getLength(); i++) {
            Element applied = (Element) appliedProfiles.item(i);
            if (applied.hasAttribute("href")) {
                String value = applied.getAttribute("href");
                if (value.contains("uml-vm-profile.profile.uml")) {
                    String fragment = extractFragment(value);
                    applied.setAttribute("href", base + fragment);
                    matches += 1;
                }
            }
        }

        return matches;
    }

    private int setAppliedProfileFragment(Element profileApplication, String profileRootId) {
        if (profileApplication == null) {
            return 0;
        }
        int matches = 0;
        NodeList appliedProfiles = profileApplication.getElementsByTagName(APPLIED_PROFILE);
        for (int i = 0; i < appliedProfiles.getLength(); i++) {
            Element applied = (Element) appliedProfiles.item(i);
            if (applied.hasAttribute("href")) {
                String value = applied.getAttribute("href");
                if (value.contains("uml-vm-profile.profile.uml")) {
                    String base = stripFragment(value);
                    applied.setAttribute("href", base + "#" + profileRootId);
                    matches += 1;
                }
            }
        }
        return matches;
    }

    private int setReferencesFragment(Element profileApplication, String epackageId) {
        if (profileApplication == null) {
            return 0;
        }
        int matches = 0;
        NodeList references = profileApplication.getElementsByTagName(REFERENCES);
        for (int i = 0; i < references.getLength(); i++) {
            Element ref = (Element) references.item(i);
            if (!isUmlAnnotation(ref.getParentNode())) {
                continue;
            }
            if (ref.hasAttribute("href")) {
                String value = ref.getAttribute("href");
                if (value.contains("uml-vm-profile.profile.uml")) {
                    String base = stripFragment(value);
                    ref.setAttribute("href", base + "#" + epackageId);
                    matches += 1;
                }
            }
        }
        return matches;
    }

    private boolean isUmlAnnotation(Node node) {
        if (!(node instanceof Element)) {
            return false;
        }
        Element element = (Element) node;
        if (!EANNOTATIONS.equals(element.getNodeName())) {
            return false;
        }
        return UML_ANNOTATION_SOURCE.equals(element.getAttribute("source"));
    }

    private int setNamespace(Document document, String target) {
        if (document == null || target == null) {
            return 0;
        }
        Element root = document.getDocumentElement();
        if (root == null) {
            return 0;
        }
        root.setAttribute("xmlns:" + NamespaceDecisionResolver.UMLVMPROFILE_PREFIX, target);
        int removals = removeNamespaceOverrides(document);
        return 1 + removals;
    }

    private int setSchemaLocation(Document document, String namespaceUri, String epackageId) {
        if (document == null || namespaceUri == null || namespaceUri.isBlank() || epackageId == null || epackageId.isBlank()) {
            return 0;
        }
        Element root = document.getDocumentElement();
        if (root == null) {
            return 0;
        }
        String value = namespaceUri + " " + PROFILE_FILENAME + "#" + epackageId;
        root.setAttribute("xsi:schemaLocation", value);
        return 1;
    }

    private int removeNamespaceOverrides(Document document) {
        if (document == null) {
            return 0;
        }
        String attrName = "xmlns:" + NamespaceDecisionResolver.UMLVMPROFILE_PREFIX;
        int removed = 0;
        NodeList all = document.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node node = all.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (element != document.getDocumentElement() && element.hasAttribute(attrName)) {
                    element.removeAttribute(attrName);
                    removed += 1;
                }
            }
        }
        return removed;
    }

    private String extractFragment(String href) {
        int idx = href.indexOf('#');
        if (idx >= 0) {
            return href.substring(idx);
        }
        return "";
    }

    private String stripFragment(String href) {
        int idx = href.indexOf('#');
        if (idx >= 0) {
            return href.substring(0, idx);
        }
        return href;
    }

    public static final class TransformResult {
        private final Document document;
        private final List<RuleLog> ruleLogs;
        private final List<String> rulesApplied;

        private TransformResult(Document document, List<RuleLog> ruleLogs, List<String> rulesApplied) {
            this.document = document;
            this.ruleLogs = ruleLogs == null ? new ArrayList<>() : new ArrayList<>(ruleLogs);
            this.rulesApplied = rulesApplied == null ? new ArrayList<>() : new ArrayList<>(rulesApplied);
        }

        public Document getDocument() {
            return document;
        }

        public List<RuleLog> getRuleLogs() {
            return new ArrayList<>(ruleLogs);
        }

        public List<String> getRulesApplied() {
            return new ArrayList<>(rulesApplied);
        }
    }


    private static final class ProfileApplicationSelection {
        private final Element retainedProfileApplication;
        private final List<Element> allProfileApplications;
        private final Set<String> allowedRemovedIds = new HashSet<>();

        private ProfileApplicationSelection(Element retainedProfileApplication, List<Element> allProfileApplications) {
            this.retainedProfileApplication = retainedProfileApplication;
            this.allProfileApplications = allProfileApplications == null ? new ArrayList<>() : allProfileApplications;
        }
    }
}
