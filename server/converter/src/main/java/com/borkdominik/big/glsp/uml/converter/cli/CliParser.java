package com.borkdominik.big.glsp.uml.converter.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public final class CliParser {
    private static final List<String> KNOWN_FLAGS = Arrays.asList(
        "--input",
        "--output",
        "--profile",
        "--report-dir",
        "--help",
        "--version"
    );

    public CliArguments parse(String[] args) {
        if (args == null) {
            throw new CliException(CliErrorCode.CLI_INVALID_ARGUMENT_VALUE, "No arguments provided");
        }

        Path input = null;
        Path output = null;
        Path profile = null;
        Path reportDir = null;
        boolean help = false;
        boolean version = false;

        int index = 0;
        while (index < args.length) {
            String token = args[index];
            if (token == null || token.trim().isEmpty()) {
                throw new CliException(CliErrorCode.CLI_INVALID_ARGUMENT_VALUE, "Empty argument");
            }
            if ("--help".equals(token)) {
                help = true;
                index += 1;
                continue;
            }
            if ("--version".equals(token)) {
                version = true;
                index += 1;
                continue;
            }

            if (!KNOWN_FLAGS.contains(token)) {
                throw new CliException(CliErrorCode.CLI_UNKNOWN_ARGUMENT, "Unknown argument: " + token);
            }

            if (index + 1 >= args.length) {
                throw new CliException(CliErrorCode.CLI_INVALID_ARGUMENT_VALUE, "Missing value for " + token);
            }

            String value = args[index + 1];
            if (value == null || value.trim().isEmpty()) {
                throw new CliException(CliErrorCode.CLI_INVALID_ARGUMENT_VALUE, "Empty value for " + token);
            }

            switch (token) {
                case "--input":
                    input = Paths.get(value);
                    break;
                case "--output":
                    output = Paths.get(value);
                    break;
                case "--profile":
                    profile = Paths.get(value);
                    break;
                case "--report-dir":
                    reportDir = Paths.get(value);
                    break;
                default:
                    throw new CliException(CliErrorCode.CLI_UNKNOWN_ARGUMENT, "Unknown argument: " + token);
            }

            index += 2;
        }

        return new CliArguments(input, output, profile, reportDir, help, version);
    }
}
