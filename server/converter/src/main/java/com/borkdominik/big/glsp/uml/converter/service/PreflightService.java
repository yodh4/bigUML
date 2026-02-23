package com.borkdominik.big.glsp.uml.converter.service;

import com.borkdominik.big.glsp.uml.converter.cli.CliErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PreflightService {
    public PreflightResult run(Path input, Path output, Path profile, Path reportDir) {
        List<PreflightCheck> checks = new ArrayList<>();

        if (input == null || !Files.exists(input) || !Files.isRegularFile(input)) {
            checks.add(new PreflightCheck("input_exists", false, "Input file not found"));
            return PreflightResult.fail(CliErrorCode.PREFLIGHT_INPUT_NOT_FOUND, "Input file not found", checks);
        }
        checks.add(new PreflightCheck("input_exists", true, input.toString()));

        String inputName = input.getFileName() == null ? "" : input.getFileName().toString();
        if (!inputName.endsWith(".uml")) {
            checks.add(new PreflightCheck("input_extension", false, "Input is not a .uml file"));
            return PreflightResult.fail(CliErrorCode.PREFLIGHT_INPUT_NOT_UML, "Input is not a .uml file", checks);
        }
        checks.add(new PreflightCheck("input_extension", true, ".uml"));

        if (output == null) {
            checks.add(new PreflightCheck("output_path", false, "Output path missing"));
            return PreflightResult.fail(
                CliErrorCode.PREFLIGHT_OUTPUT_DIR_MISSING,
                "Output path missing",
                checks
            );
        }

        Path outputDir = output.getParent();
        if (outputDir == null) {
            outputDir = Path.of(".").toAbsolutePath().normalize();
        }
        if (!Files.exists(outputDir) || !Files.isDirectory(outputDir)) {
            checks.add(new PreflightCheck("output_dir", false, "Output directory missing"));
            return PreflightResult.fail(
                CliErrorCode.PREFLIGHT_OUTPUT_DIR_MISSING,
                "Output directory missing",
                checks
            );
        }
        checks.add(new PreflightCheck("output_dir", true, outputDir.toString()));
        if (!Files.isWritable(outputDir)) {
            checks.add(new PreflightCheck("output_writable", false, "Output directory not writable"));
            return PreflightResult.fail(
                CliErrorCode.PREFLIGHT_OUTPUT_NOT_WRITABLE,
                "Output directory not writable",
                checks
            );
        }
        checks.add(new PreflightCheck("output_writable", true, outputDir.toString()));

        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) {
            checks.add(new PreflightCheck("input_output_distinct", false, "Input and output are identical"));
            return PreflightResult.fail(
                CliErrorCode.PREFLIGHT_INPUT_OUTPUT_SAME,
                "Input and output paths must be different",
                checks
            );
        }
        checks.add(new PreflightCheck("input_output_distinct", true, "Different paths"));

        if (profile == null || !Files.exists(profile) || !Files.isRegularFile(profile)) {
            checks.add(new PreflightCheck("profile_exists", false, "Profile file not found"));
            return PreflightResult.fail(
                CliErrorCode.PREFLIGHT_PROFILE_NOT_FOUND,
                "Profile file not found",
                checks
            );
        }
        checks.add(new PreflightCheck("profile_exists", true, profile.toString()));

        Path resolvedReportDir = reportDir == null ? outputDir : reportDir;
        if (!Files.exists(resolvedReportDir) || !Files.isDirectory(resolvedReportDir)) {
            checks.add(new PreflightCheck("report_dir", false, "Report directory missing"));
            return PreflightResult.fail(
                CliErrorCode.PREFLIGHT_OUTPUT_DIR_MISSING,
                "Report directory missing",
                checks
            );
        }
        checks.add(new PreflightCheck("report_dir", true, resolvedReportDir.toString()));

        return PreflightResult.ok(checks);
    }
}
