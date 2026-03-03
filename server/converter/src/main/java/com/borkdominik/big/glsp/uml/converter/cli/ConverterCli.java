package com.borkdominik.big.glsp.uml.converter.cli;

import com.borkdominik.big.glsp.uml.converter.report.ConversionReport;
import com.borkdominik.big.glsp.uml.converter.report.ConversionReportWriter;
import com.borkdominik.big.glsp.uml.converter.service.ConverterService;
import com.borkdominik.big.glsp.uml.converter.service.TransformException;
import com.borkdominik.big.glsp.uml.converter.service.PreflightResult;
import com.borkdominik.big.glsp.uml.converter.service.PreflightService;
import com.borkdominik.big.glsp.uml.converter.service.ProfileMetadataException;
import com.borkdominik.big.glsp.uml.converter.service.ProfileMetadataResolver;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Instant;

public final class ConverterCli {
    public static final String TOOL_NAME = "biguml-uml2winvmj-converter";
    public static final String TOOL_VERSION = "0.1.0";
    public static final String STAGE = "2";

    public static void main(String[] args) {
        ConverterCli cli = new ConverterCli();
        int exitCode = cli.run(args, new PrintWriter(System.err, true));
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args, PrintWriter errorWriter) {
        CliParser parser = new CliParser();
        ConversionReport report = ConversionReport.empty(TOOL_NAME, TOOL_VERSION, STAGE, Instant.now());

        try {
            CliArguments parsed = parser.parse(args);
            if (parsed.isShowHelp()) {
                errorWriter.println(helpText());
                return 0;
            }
            if (parsed.isShowVersion()) {
                errorWriter.println(TOOL_NAME + " " + TOOL_VERSION);
                return 0;
            }

            Path inputPath = parsed.getInputPath();
            Path outputPath = parsed.getOutputPath();
            Path profilePath = parsed.getProfilePath();
            Path reportDir = parsed.getReportDir();

            if (inputPath == null || outputPath == null || profilePath == null) {
                throw new CliException(CliErrorCode.CLI_MISSING_REQUIRED_ARGUMENT, "Missing required arguments");
            }

            if (reportDir == null) {
                reportDir = outputPath.getParent();
                if (reportDir == null) {
                    reportDir = Path.of(".").toAbsolutePath().normalize();
                }
            }

            report = report.withInputs(inputPath, outputPath, profilePath, reportDir);

            PreflightService preflightService = new PreflightService();
            PreflightResult preflight = preflightService.run(inputPath, outputPath, profilePath, reportDir);
            report = report.withPreflight(preflight);

            if (!preflight.isOk()) {
                report = report.failed(preflight.getErrorCode().getCode(), preflight.getMessage());
                return writeReportAndExit(report, errorWriter, preflight.getErrorCode());
            }

            ProfileMetadataResolver.ResolutionResult resolution;
            try {
                ProfileMetadataResolver resolver = new ProfileMetadataResolver();
                resolution = resolver.resolve(profilePath);
                report = report.withMetadata(resolution.getMetadata());
                report = report.withWarnings(resolution.getWarnings());
            } catch (ProfileMetadataException resolveError) {
                report = report.failed(resolveError.getErrorCode().getCode(), resolveError.getMessage());
                return writeReportAndExit(report, errorWriter, resolveError.getErrorCode());
            } catch (Exception resolveError) {
                report = report.failed(CliErrorCode.RESOLVE_PROFILE_INVALID.getCode(), resolveError.getMessage());
                return writeReportAndExit(report, errorWriter, CliErrorCode.RESOLVE_PROFILE_INVALID);
            }

            try {
                ConverterService converter = new ConverterService();
                ConverterService.ConversionResult result = converter.convert(
                    inputPath,
                    outputPath,
                    resolution.getMetadata()
                );
                report = report.withNamespaceDecision(result.getNamespaceDecision());
                report = report.withRuleLogs(result.getRuleLogs());
                report = report.withConversion("critical-transform", result.getRulesApplied());
            } catch (TransformException transformError) {
                report = report.failed(CliErrorCode.TRANSFORM_FAILED.getCode(), transformError.getMessage());
                return writeReportAndExit(report, errorWriter, CliErrorCode.TRANSFORM_FAILED);
            } catch (java.io.IOException ioError) {
                report = report.failed(CliErrorCode.IO_OUTPUT_WRITE_FAILED.getCode(), ioError.getMessage());
                return writeReportAndExit(report, errorWriter, CliErrorCode.IO_OUTPUT_WRITE_FAILED);
            } catch (Exception transformError) {
                report = report.failed(CliErrorCode.TRANSFORM_FAILED.getCode(), transformError.getMessage());
                return writeReportAndExit(report, errorWriter, CliErrorCode.TRANSFORM_FAILED);
            }

            report = report.success("Critical transform completed");

            return writeReportAndExit(report, errorWriter, CliErrorCode.NONE);
        } catch (CliException exception) {
            report = report.failed(exception.getErrorCode().getCode(), exception.getMessage());
            return writeReportAndExit(report, errorWriter, exception.getErrorCode());
        } catch (Exception exception) {
            report = report.failed(CliErrorCode.IO_OUTPUT_WRITE_FAILED.getCode(), exception.getMessage());
            return writeReportAndExit(report, errorWriter, CliErrorCode.IO_OUTPUT_WRITE_FAILED);
        }
    }

    private int writeReportAndExit(
        ConversionReport report,
        PrintWriter errorWriter,
        CliErrorCode exitCode
    ) {
        try {
            ConversionReportWriter writer = new ConversionReportWriter();
            writer.write(report);
        } catch (Exception reportError) {
            errorWriter.println("E-IO-002 Failed to write report: " + reportError.getMessage());
            return 1;
        }

        if (exitCode == CliErrorCode.NONE) {
            return 0;
        }
        if (!exitCode.getCode().isEmpty()) {
            errorWriter.println(exitCode.getCode() + " " + report.getStatus().getMessage());
        }
        return 1;
    }

    private String helpText() {
        return String.join(
            System.lineSeparator(),
            "Usage:",
            "  --input <path> --output <path> --profile <path> [--report-dir <path>] [--help] [--version]",
            "",
            "Required:",
            "  --input <path>       Source .uml file",
            "  --output <path>      Output .uml file",
            "  --profile <path>     Canonical profile .profile.uml",
            "",
            "Optional:",
            "  --report-dir <path>  Directory for conversion-report.json/md (default: output directory)",
            "  --help               Show this help",
            "  --version            Show tool version"
        );
    }
}
