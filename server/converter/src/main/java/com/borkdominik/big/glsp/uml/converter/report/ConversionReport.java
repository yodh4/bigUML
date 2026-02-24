package com.borkdominik.big.glsp.uml.converter.report;

import com.borkdominik.big.glsp.uml.converter.service.PreflightCheck;
import com.borkdominik.big.glsp.uml.converter.service.PreflightResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConversionReport {
    private final ToolInfo tool;
    private final Instant timestamp;
    private final InputInfo inputs;
    private final StatusInfo status;
    private final PreflightInfo preflight;
    private final ProfileMetadata metadata;
    private final ConversionInfo conversion;
    private final List<String> warnings;
    private final List<String> errors;

    private ConversionReport(
        ToolInfo tool,
        Instant timestamp,
        InputInfo inputs,
        StatusInfo status,
        PreflightInfo preflight,
        ProfileMetadata metadata,
        ConversionInfo conversion,
        List<String> warnings,
        List<String> errors
    ) {
        this.tool = tool;
        this.timestamp = timestamp;
        this.inputs = inputs;
        this.status = status;
        this.preflight = preflight;
        this.metadata = metadata;
        this.conversion = conversion;
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        this.errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
    }

    public static ConversionReport empty(String toolName, String version, String stage, Instant now) {
        return new ConversionReport(
            new ToolInfo(toolName, version, stage),
            now,
            InputInfo.empty(),
            StatusInfo.pending(),
            PreflightInfo.empty(),
            ProfileMetadata.empty(),
            ConversionInfo.empty(),
            new ArrayList<>(),
            new ArrayList<>()
        );
    }

    public ConversionReport withInputs(Path input, Path output, Path profile, Path reportDir) {
        return new ConversionReport(
            tool,
            timestamp,
            new InputInfo(input, output, profile, reportDir),
            status,
            preflight,
            metadata,
            conversion,
            warnings,
            errors
        );
    }

    public ConversionReport withPreflight(PreflightResult result) {
        List<PreflightCheck> checks = result == null ? new ArrayList<>() : result.getChecks();
        return new ConversionReport(
            tool,
            timestamp,
            inputs,
            status,
            new PreflightInfo(checks),
            metadata,
            conversion,
            warnings,
            errors
        );
    }

    public ConversionReport withMetadata(ProfileMetadata metadata) {
        return new ConversionReport(
            tool,
            timestamp,
            inputs,
            status,
            preflight,
            metadata,
            conversion,
            warnings,
            errors
        );
    }

    public ConversionReport withConversionMode(String mode) {
        return new ConversionReport(
            tool,
            timestamp,
            inputs,
            status,
            preflight,
            metadata,
            new ConversionInfo(mode, Collections.emptyList()),
            warnings,
            errors
        );
    }

    public ConversionReport success(String message) {
        return new ConversionReport(
            tool,
            timestamp,
            inputs,
            StatusInfo.success(message),
            preflight,
            metadata,
            conversion,
            warnings,
            errors
        );
    }

    public ConversionReport failed(String errorCode, String message) {
        return new ConversionReport(
            tool,
            timestamp,
            inputs,
            StatusInfo.failure(errorCode, message),
            preflight,
            metadata,
            conversion,
            warnings,
            errors
        );
    }

    public ToolInfo getTool() {
        return tool;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public InputInfo getInputs() {
        return inputs;
    }

    public StatusInfo getStatus() {
        return status;
    }

    public PreflightInfo getPreflight() {
        return preflight;
    }

    public ProfileMetadata getMetadata() {
        return metadata;
    }

    public ConversionInfo getConversion() {
        return conversion;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public ConversionReport withWarnings(List<String> warnings) {
        List<String> mergedWarnings = new ArrayList<>(this.warnings);
        if (warnings != null && !warnings.isEmpty()) {
            mergedWarnings.addAll(warnings);
        }
        return new ConversionReport(
            tool,
            timestamp,
            inputs,
            status,
            preflight,
            metadata,
            conversion,
            mergedWarnings,
            errors
        );
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public static final class ToolInfo {
        private final String name;
        private final String version;
        private final String stage;

        public ToolInfo(String name, String version, String stage) {
            this.name = name;
            this.version = version;
            this.stage = stage;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getStage() {
            return stage;
        }
    }

    public static final class InputInfo {
        private final String inputPath;
        private final String outputPath;
        private final String profilePath;
        private final String reportDir;

        public InputInfo(Path inputPath, Path outputPath, Path profilePath, Path reportDir) {
            this.inputPath = inputPath == null ? null : inputPath.toString();
            this.outputPath = outputPath == null ? null : outputPath.toString();
            this.profilePath = profilePath == null ? null : profilePath.toString();
            this.reportDir = reportDir == null ? null : reportDir.toString();
        }

        public static InputInfo empty() {
            return new InputInfo(null, null, null, null);
        }

        public String getInputPath() {
            return inputPath;
        }

        public String getOutputPath() {
            return outputPath;
        }

        public String getProfilePath() {
            return profilePath;
        }

        public String getReportDir() {
            return reportDir;
        }
    }

    public static final class StatusInfo {
        private final String result;
        private final String errorCode;
        private final String message;

        private StatusInfo(String result, String errorCode, String message) {
            this.result = result;
            this.errorCode = errorCode;
            this.message = message;
        }

        public static StatusInfo pending() {
            return new StatusInfo("pending", null, "");
        }

        public static StatusInfo success(String message) {
            return new StatusInfo("success", null, message);
        }

        public static StatusInfo failure(String errorCode, String message) {
            return new StatusInfo("failure", errorCode, message);
        }

        public String getResult() {
            return result;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class PreflightInfo {
        private final List<PreflightCheck> checks;

        public PreflightInfo(List<PreflightCheck> checks) {
            this.checks = checks == null ? new ArrayList<>() : new ArrayList<>(checks);
        }

        public static PreflightInfo empty() {
            return new PreflightInfo(new ArrayList<>());
        }

        public List<PreflightCheck> getChecks() {
            return Collections.unmodifiableList(checks);
        }
    }

    public static final class ConversionInfo {
        private final String mode;
        private final List<String> rulesApplied;

        public ConversionInfo(String mode, List<String> rulesApplied) {
            this.mode = mode;
            this.rulesApplied = rulesApplied == null ? new ArrayList<>() : new ArrayList<>(rulesApplied);
        }

        public static ConversionInfo empty() {
            return new ConversionInfo("", new ArrayList<>());
        }

        public String getMode() {
            return mode;
        }

        public List<String> getRulesApplied() {
            return Collections.unmodifiableList(rulesApplied);
        }
    }
}
