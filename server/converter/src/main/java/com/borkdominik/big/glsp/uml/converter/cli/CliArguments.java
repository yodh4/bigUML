package com.borkdominik.big.glsp.uml.converter.cli;

import java.nio.file.Path;

public final class CliArguments {
    private final Path inputPath;
    private final Path outputPath;
    private final Path profilePath;
    private final Path reportDir;
    private final boolean showHelp;
    private final boolean showVersion;

    public CliArguments(
        Path inputPath,
        Path outputPath,
        Path profilePath,
        Path reportDir,
        boolean showHelp,
        boolean showVersion
    ) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.profilePath = profilePath;
        this.reportDir = reportDir;
        this.showHelp = showHelp;
        this.showVersion = showVersion;
    }

    public Path getInputPath() {
        return inputPath;
    }

    public Path getOutputPath() {
        return outputPath;
    }

    public Path getProfilePath() {
        return profilePath;
    }

    public Path getReportDir() {
        return reportDir;
    }

    public boolean isShowHelp() {
        return showHelp;
    }

    public boolean isShowVersion() {
        return showVersion;
    }
}
