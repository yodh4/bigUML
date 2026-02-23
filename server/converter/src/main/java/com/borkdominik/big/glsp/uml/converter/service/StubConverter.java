package com.borkdominik.big.glsp.uml.converter.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class StubConverter {
    public void copy(Path input, Path output) throws IOException {
        Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
    }
}
