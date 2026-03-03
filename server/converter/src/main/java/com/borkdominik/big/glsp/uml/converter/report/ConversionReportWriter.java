package com.borkdominik.big.glsp.uml.converter.report;

import com.borkdominik.big.glsp.uml.converter.report.NamespaceDecision;
import com.borkdominik.big.glsp.uml.converter.report.ProfileMetadata;
import com.borkdominik.big.glsp.uml.converter.report.RuleLog;
import com.borkdominik.big.glsp.uml.converter.service.PreflightCheck;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.List;

public final class ConversionReportWriter {
    private static final String JSON_FILE = "conversion-report.json";
    private static final String MD_FILE = "conversion-report.md";

    private final Gson gson;

    public ConversionReportWriter() {
        this.gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantSerializer())
            .setPrettyPrinting()
            .create();
    }

    public void write(ConversionReport report) throws IOException {
        String reportDirValue = report.getInputs().getReportDir();
        Path reportDir = reportDirValue == null || reportDirValue.trim().isEmpty()
            ? Path.of(".").toAbsolutePath().normalize()
            : Path.of(reportDirValue);
        Files.createDirectories(reportDir);
        writeJson(report, reportDir.resolve(JSON_FILE));
        writeMarkdown(report, reportDir.resolve(MD_FILE));
    }

    private void writeJson(ConversionReport report, Path target) throws IOException {
        String json = gson.toJson(report);
        Files.writeString(target, json, StandardCharsets.UTF_8);
    }

    private void writeMarkdown(ConversionReport report, Path target) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("# Conversion Report").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("## Run Summary").append(System.lineSeparator());
        builder.append("- Status: ").append(report.getStatus().getResult()).append(System.lineSeparator());
        builder.append("- Error Code: ").append(valueOrDash(report.getStatus().getErrorCode())).append(System.lineSeparator());
        builder.append("- Message: ").append(valueOrDash(report.getStatus().getMessage())).append(System.lineSeparator());
        builder.append("- Timestamp: ")
            .append(DateTimeFormatter.ISO_INSTANT.format(report.getTimestamp()))
            .append(System.lineSeparator());
        builder.append(System.lineSeparator());

        builder.append("## Inputs").append(System.lineSeparator());
        builder.append("- Input: ").append(valueOrDash(report.getInputs().getInputPath())).append(System.lineSeparator());
        builder.append("- Output: ").append(valueOrDash(report.getInputs().getOutputPath())).append(System.lineSeparator());
        builder.append("- Profile: ").append(valueOrDash(report.getInputs().getProfilePath())).append(System.lineSeparator());
        builder.append("- Report Dir: ").append(valueOrDash(report.getInputs().getReportDir())).append(System.lineSeparator());
        builder.append(System.lineSeparator());

        builder.append("## Preflight").append(System.lineSeparator());
        List<PreflightCheck> checks = report.getPreflight().getChecks();
        if (checks.isEmpty()) {
            builder.append("- No checks recorded").append(System.lineSeparator());
        } else {
            for (PreflightCheck check : checks) {
                builder.append("- ")
                    .append(check.getId())
                    .append(": ")
                    .append(check.isOk() ? "ok" : "fail")
                    .append(" - ")
                    .append(valueOrDash(check.getDetail()))
                    .append(System.lineSeparator());
            }
        }
        builder.append(System.lineSeparator());

        builder.append("## Metadata Resolver").append(System.lineSeparator());
        ProfileMetadata metadata = report.getMetadata();
        if (metadata == null || metadata.getProfileRootId() == null || metadata.getEpackageId() == null) {
            builder.append("- Status: not available").append(System.lineSeparator());
        } else {
            builder.append("- Profile Root ID: ")
                .append(valueOrDash(metadata.getProfileRootId()))
                .append(System.lineSeparator());
            builder.append("- EPackage ID: ")
                .append(valueOrDash(metadata.getEpackageId()))
                .append(System.lineSeparator());
            builder.append("- Selection Strategy: ")
                .append(valueOrDash(metadata.getSelectionStrategy()))
                .append(System.lineSeparator());
            builder.append("- EPackage Version: ")
                .append(valueOrDash(metadata.getEpackageVersion()))
                .append(System.lineSeparator());
            builder.append("- EPackage Date: ")
                .append(valueOrDash(metadata.getEpackageDate()))
                .append(System.lineSeparator());
            builder.append("- EPackage nsURI: ")
                .append(valueOrDash(metadata.getEpackageNsUri()))
                .append(System.lineSeparator());
            builder.append("- Candidate Count: ")
                .append(metadata.getCandidateCount() == null ? "-" : metadata.getCandidateCount())
                .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Namespace Resolution").append(System.lineSeparator());
        NamespaceDecision decision = report.getNamespaceDecision();
        if (decision == null || decision.getSelectedValue() == null) {
            builder.append("- Status: not available").append(System.lineSeparator());
        } else {
            builder.append("- Current: ")
                .append(valueOrDash(decision.getCurrentValue()))
                .append(System.lineSeparator());
            builder.append("- Selected: ")
                .append(valueOrDash(decision.getSelectedValue()))
                .append(System.lineSeparator());
            builder.append("- Ambiguous: ")
                .append(decision.isAmbiguous())
                .append(System.lineSeparator());
            builder.append("- Basis: ")
                .append(valueOrDash(decision.getDecisionBasis()))
                .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());

        builder.append("## Rules Applied").append(System.lineSeparator());
        List<RuleLog> rules = report.getRules();
        if (rules.isEmpty()) {
            builder.append("- none").append(System.lineSeparator());
        } else {
            for (RuleLog rule : rules) {
                builder.append("- ")
                    .append(rule.getId())
                    .append(": matches=")
                    .append(rule.getMatches())
                    .append(", status=")
                    .append(valueOrDash(rule.getStatus()))
                    .append(", summary=")
                    .append(valueOrDash(rule.getSummary()))
                    .append(System.lineSeparator());
            }
        }
        builder.append(System.lineSeparator());

        builder.append("## Conversion").append(System.lineSeparator());
        builder.append("- Mode: ").append(valueOrDash(report.getConversion().getMode())).append(System.lineSeparator());
        builder.append("- Rules Applied: ")
            .append(report.getConversion().getRulesApplied().isEmpty() ? "none" : String.join(", ", report.getConversion().getRulesApplied()))
            .append(System.lineSeparator());
        builder.append(System.lineSeparator());

        builder.append("## Warnings").append(System.lineSeparator());
        if (report.getWarnings().isEmpty()) {
            builder.append("- none").append(System.lineSeparator());
        } else {
            for (String warning : report.getWarnings()) {
                builder.append("- ").append(warning).append(System.lineSeparator());
            }
        }
        builder.append(System.lineSeparator());

        builder.append("## Errors").append(System.lineSeparator());
        if (report.getErrors().isEmpty()) {
            builder.append("- none").append(System.lineSeparator());
        } else {
            for (String error : report.getErrors()) {
                builder.append("- ").append(error).append(System.lineSeparator());
            }
        }

        Files.writeString(target, builder.toString(), StandardCharsets.UTF_8);
    }

    private String valueOrDash(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        return value;
    }

    private static final class InstantSerializer implements JsonSerializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return new JsonPrimitive("");
            }
            return new JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(src));
        }
    }
}
