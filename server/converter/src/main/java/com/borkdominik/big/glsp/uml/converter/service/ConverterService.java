package com.borkdominik.big.glsp.uml.converter.service;

import com.borkdominik.big.glsp.uml.converter.report.NamespaceDecision;
import com.borkdominik.big.glsp.uml.converter.report.ProfileMetadata;
import com.borkdominik.big.glsp.uml.converter.report.RuleLog;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.w3c.dom.Document;

public final class ConverterService {
    public ConversionResult convert(Path input, Path output, ProfileMetadata metadata) throws IOException, TransformException {
        XmlDocumentService xmlService = new XmlDocumentService();
        Document document = xmlService.read(input);

        NamespaceDecisionResolver resolver = new NamespaceDecisionResolver();
        NamespaceDecision decision = resolver.resolve(document, metadata);

        TransformEngine engine = new TransformEngine();
        TransformEngine.TransformResult result = engine.apply(document, metadata, decision);

        xmlService.write(result.getDocument(), output);

        return new ConversionResult(decision, result.getRuleLogs(), result.getRulesApplied());
    }

    public static final class ConversionResult {
        private final NamespaceDecision namespaceDecision;
        private final List<RuleLog> ruleLogs;
        private final List<String> rulesApplied;

        private ConversionResult(NamespaceDecision namespaceDecision, List<RuleLog> ruleLogs, List<String> rulesApplied) {
            this.namespaceDecision = namespaceDecision;
            this.ruleLogs = ruleLogs;
            this.rulesApplied = rulesApplied;
        }

        public NamespaceDecision getNamespaceDecision() {
            return namespaceDecision;
        }

        public List<RuleLog> getRuleLogs() {
            return ruleLogs;
        }

        public List<String> getRulesApplied() {
            return rulesApplied;
        }
    }
}
