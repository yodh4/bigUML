package com.borkdominik.big.glsp.uml.converter.service;

import com.borkdominik.big.glsp.uml.converter.cli.CliErrorCode;
import com.borkdominik.big.glsp.uml.converter.report.ProfileMetadata;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.resource.UMLResource;

public final class ProfileMetadataResolver {
    private static final String UML_ANNOTATION_SOURCE = "http://www.eclipse.org/uml2/2.0.0/UML";
    private static final String PAPYRUS_VERSION_SOURCE = "PapyrusVersion";
    private static final String VERSION_KEY = "Version";
    private static final String DATE_KEY = "Date";

    public ResolutionResult resolve(Path profilePath) throws ProfileMetadataException {
        if (profilePath == null) {
            throw new ProfileMetadataException(CliErrorCode.RESOLVE_PROFILE_INVALID, "Profile path is required");
        }

        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("uml", UMLResource.Factory.INSTANCE);
        resourceSet.getPackageRegistry().put(UMLPackage.eNS_URI, UMLPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);

        URI profileUri = URI.createFileURI(profilePath.toAbsolutePath().normalize().toString());
        Resource resource;
        try {
            resource = resourceSet.getResource(profileUri, true);
        } catch (Exception loadError) {
            throw new ProfileMetadataException(
                CliErrorCode.RESOLVE_PROFILE_INVALID,
                "Profile could not be loaded: " + loadError.getMessage()
            );
        }
        if (resource == null || resource.getContents().isEmpty()) {
            throw new ProfileMetadataException(CliErrorCode.RESOLVE_PROFILE_INVALID, "Profile could not be loaded");
        }

        Object root = resource.getContents().get(0);
        if (!(root instanceof Profile)) {
            throw new ProfileMetadataException(CliErrorCode.RESOLVE_PROFILE_INVALID, "Profile root is invalid");
        }

        Profile profile = (Profile) root;
        String profileRootId = resolveId(resource, profile);
        if (profileRootId == null || profileRootId.isBlank()) {
            throw new ProfileMetadataException(CliErrorCode.RESOLVE_PROFILE_INVALID, "Profile root ID missing");
        }

        List<EPackageCandidate> candidates = collectCandidates(resource, profile);
        if (candidates.isEmpty()) {
            throw new ProfileMetadataException(CliErrorCode.RESOLVE_EPACKAGE_NOT_FOUND, "No EPackage candidates found");
        }

        EPackageCandidate selected = selectCandidate(candidates);
        if (selected == null || selected.id == null || selected.id.isBlank()) {
            throw new ProfileMetadataException(CliErrorCode.RESOLVE_SELECTION_FAILED, "Failed to select EPackage");
        }

        String selectionStrategy = selected.selectionStrategy;
        ProfileMetadata metadata = new ProfileMetadata(
            profilePath.toString(),
            profileRootId,
            selected.id,
            selectionStrategy,
            selected.nsUri,
            selected.versionRaw,
            selected.dateRaw,
            candidates.size()
        );

        return new ResolutionResult(metadata, selected.warnings);
    }

    private String resolveId(Resource resource, EObject object) {
        if (resource instanceof XMLResource) {
            String id = ((XMLResource) resource).getID(object);
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return resource.getURIFragment(object);
    }

    private List<EPackageCandidate> collectCandidates(Resource resource, Profile profile) {
        List<EPackageCandidate> candidates = new ArrayList<>();
        int index = 0;
        for (EAnnotation annotation : profile.getEAnnotations()) {
            if (!UML_ANNOTATION_SOURCE.equals(annotation.getSource())) {
                continue;
            }
            for (Object content : annotation.getContents()) {
                if (!(content instanceof EPackage)) {
                    continue;
                }
                EPackage ePackage = (EPackage) content;
                EPackageCandidate candidate = buildCandidate(resource, ePackage, index++);
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private EPackageCandidate buildCandidate(Resource resource, EPackage ePackage, int index) {
        String id = resolveId(resource, ePackage);
        String nsUri = ePackage.getNsURI();

        String version = null;
        String date = null;
        for (EAnnotation annotation : ePackage.getEAnnotations()) {
            if (!PAPYRUS_VERSION_SOURCE.equals(annotation.getSource())) {
                continue;
            }
            version = annotation.getDetails().get(VERSION_KEY);
            date = annotation.getDetails().get(DATE_KEY);
            break;
        }

        return new EPackageCandidate(id, nsUri, version, date, index);
    }

    private EPackageCandidate selectCandidate(List<EPackageCandidate> candidates) throws ProfileMetadataException {
        List<EPackageCandidate> sorted = new ArrayList<>(candidates);
        Comparator<EPackageCandidate> comparator = Comparator
            .comparing(EPackageCandidate::versionParsed, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(EPackageCandidate::dateParsed, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(candidate -> candidate.index);
        sorted.sort(comparator);

        EPackageCandidate best = sorted.get(0);
        if (best == null) {
            throw new ProfileMetadataException(CliErrorCode.RESOLVE_SELECTION_FAILED, "Failed to select EPackage");
        }

        best.selectionStrategy = "version-desc -> date-desc -> doc-order";

        boolean hasVersion = candidates.stream().anyMatch(candidate -> candidate.versionParsed() != null);
        boolean hasDate = candidates.stream().anyMatch(candidate -> candidate.dateParsed() != null);

        EPackageCandidate secondBest = sorted.size() > 1 ? sorted.get(1) : null;
        boolean versionTie = secondBest != null && Objects.equals(best.versionParsed(), secondBest.versionParsed());
        boolean dateTie = secondBest != null && Objects.equals(best.dateParsed(), secondBest.dateParsed());

        if (!hasVersion) {
            best.warnings.add("EPackage version metadata missing; used date/doc-order fallback");
        } else if (versionTie) {
            best.warnings.add("EPackage version tie detected; used date/doc-order fallback");
        }

        if (versionTie) {
            if (!hasDate) {
                best.warnings.add("EPackage date metadata missing; used doc-order fallback");
            } else if (dateTie) {
                best.warnings.add("EPackage date tie detected; used doc-order fallback");
            }
        }

        return best;
    }

    private static final class EPackageCandidate {
        private final String id;
        private final String nsUri;
        private final String versionRaw;
        private final String dateRaw;
        private final int index;
        private String selectionStrategy;
        private final List<String> warnings = new ArrayList<>();

        private EPackageCandidate(String id, String nsUri, String versionRaw, String dateRaw, int index) {
            this.id = id;
            this.nsUri = nsUri;
            this.versionRaw = versionRaw;
            this.dateRaw = dateRaw;
            this.index = index;
        }

        private Version versionParsed() {
            if (versionRaw == null || versionRaw.isBlank()) {
                return null;
            }
            return Version.parse(versionRaw);
        }

        private LocalDate dateParsed() {
            if (dateRaw == null || dateRaw.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(dateRaw, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static final class Version implements Comparable<Version> {
        private final List<Integer> parts;

        private Version(List<Integer> parts) {
            this.parts = parts;
        }

        static Version parse(String raw) {
            if (raw == null) {
                return null;
            }
            String[] tokens = raw.trim().split("\\.");
            List<Integer> parts = new ArrayList<>();
            for (String token : tokens) {
                if (token.isEmpty()) {
                    parts.add(0);
                } else {
                    try {
                        parts.add(Integer.parseInt(token));
                    } catch (NumberFormatException ignored) {
                        parts.add(0);
                    }
                }
            }
            return new Version(parts);
        }

        @Override
        public int compareTo(Version other) {
            if (other == null) {
                return 1;
            }
            int max = Math.max(this.parts.size(), other.parts.size());
            for (int i = 0; i < max; i++) {
                int left = i < this.parts.size() ? this.parts.get(i) : 0;
                int right = i < other.parts.size() ? other.parts.get(i) : 0;
                if (left != right) {
                    return Integer.compare(left, right);
                }
            }
            return 0;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Version)) {
                return false;
            }
            Version that = (Version) other;
            return Objects.equals(this.parts, that.parts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parts);
        }
    }

    public static final class ResolutionResult {
        private final ProfileMetadata metadata;
        private final List<String> warnings;

        private ResolutionResult(ProfileMetadata metadata, List<String> warnings) {
            this.metadata = metadata;
            this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        }

        public ProfileMetadata getMetadata() {
            return metadata;
        }

        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
    }
}
