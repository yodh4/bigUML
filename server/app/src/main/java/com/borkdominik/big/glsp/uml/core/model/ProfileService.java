/********************************************************************************
 * Copyright (c) 2024 borkdominik and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0, or the MIT License which is
 * available at https://opensource.org/licenses/MIT.
 *
 * SPDX-License-Identifier: EPL-2.0 OR MIT
 ********************************************************************************/
package com.borkdominik.big.glsp.uml.core.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Stereotype;

/**
 * Service for discovering, loading, and applying UML Profiles.
 * 
 * This service scans the model directory for *.profile.uml files
 * and applies them to the UML model, making stereotypes available
 * for application to model elements.
 */
public class ProfileService {

    private static final Logger LOGGER = Logger.getLogger(ProfileService.class.getName());
    private static final String PROFILE_EXTENSION = ".profile.uml";
    private static final String CANONICAL_PROFILE_PATHMAP_PREFIX = "pathmap://model/uml-vm-profile/";
    private static final String EMBEDDED_PROFILE_CLASSPATH = "profiles/uml-vm-profile.profile.uml";

    private final List<Profile> loadedProfiles = new ArrayList<>();

    /**
     * Discovers profile files in the same directory as the model file.
     *
     * @deprecated No longer called. Profile loading now uses the embedded classpath resource
     *             via {@link #loadEmbeddedProfile(ResourceSet)}. Retained in case directory-based
     *             discovery is needed for future multi-profile support.
     *
     * @param modelUri URI of the UML model file
     * @return List of URIs for discovered profile files
     */
    @Deprecated
    public List<URI> discoverProfileUris(URI modelUri) {
        List<URI> profileUris = new ArrayList<>();

        if (modelUri == null || !modelUri.isFile()) {
            LOGGER.warning("Cannot discover profiles: model URI is null or not a file URI");
            return profileUris;
        }

        // Get the directory containing the model file
        File modelFile = new File(modelUri.toFileString());
        File modelDir = modelFile.getParentFile();

        if (modelDir == null || !modelDir.exists() || !modelDir.isDirectory()) {
            LOGGER.warning("Cannot discover profiles: model directory does not exist");
            return profileUris;
        }

        // Check only the model directory as per user requirement
        File[] profileFiles = modelDir.listFiles((dir, name) -> name.endsWith(PROFILE_EXTENSION));
        if (profileFiles != null && profileFiles.length > 0) {
            for (File profileFile : profileFiles) {
                URI profileUri = URI.createFileURI(profileFile.getAbsolutePath());
                profileUris.add(profileUri);
                LOGGER.info("Discovered profile: " + profileFile.getName() + " in " + modelDir.getAbsolutePath());
            }
        }

        return profileUris;
    }

    /**
     * Loads the embedded uml-vm-profile from the classpath into the given ResourceSet.
     * This is the preferred entry point for loading the built-in profile — it resolves
     * the JAR resource via the ClassLoader and delegates to {@link #loadProfile(URI, ResourceSet)}
     * so all URI remapping and Ecore package registration are performed identically.
     *
     * @param resourceSet ResourceSet to load the profile into
     * @return The loaded Profile, or null if the classpath resource is missing or loading failed
     */
    public Profile loadEmbeddedProfile(final ResourceSet resourceSet) {
        var profileUrl = getClass().getClassLoader().getResource(EMBEDDED_PROFILE_CLASSPATH);
        if (profileUrl == null) {
            LOGGER.severe("Embedded uml-vm-profile not found on classpath at: " + EMBEDDED_PROFILE_CLASSPATH);
            return null;
        }
        URI profileUri = URI.createURI(profileUrl.toString());
        LOGGER.info("Loading embedded uml-vm-profile from classpath: " + profileUri);
        return loadProfile(profileUri, resourceSet);
    }

    /**
     * Loads a profile from the given URI.
     * 
     * @param profileUri  URI of the profile file
     * @param resourceSet ResourceSet to use for loading
     * @return The loaded Profile, or null if loading failed
     */
    public Profile loadProfile(URI profileUri, ResourceSet resourceSet) {
        try {
            Resource resource = resourceSet.getResource(profileUri, true);

            if (resource == null || resource.getContents().isEmpty()) {
                LOGGER.warning("Failed to load profile resource: " + profileUri);
                return null;
            }

            var root = resource.getContents().get(0);
            if (root instanceof Profile) {
                Profile profile = (Profile) root;

                // Remap early so downstream profile handling and later model save use
                // canonical pathmap references.
                remapProfileResourceUri(profile, resource, resourceSet, profileUri);

                // Register the Ecore packages from the profile's annotations
                // These are needed for UML2 to recognize stereotypes as applicable
                registerProfileEcorePackages(profile, resourceSet);

                // Define the profile if not already defined
                if (!profile.isDefined()) {
                    LOGGER.info("Profile '" + profile.getName() + "' is not defined, attempting define()...");
                    try {
                        profile.define();
                        LOGGER.info("Successfully defined profile: " + profile.getName());
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Could not define profile '" + profile.getName() +
                                "': " + e.getMessage());
                    }
                } else {
                    LOGGER.info("Profile '" + profile.getName() + "' is already defined");
                }

                // loadedProfiles.add(profile); // REMOVED: Do not store stateful profiles in
                // Singleton
                LOGGER.info("Loaded profile: " + profile.getName() + " from " + profileUri +
                        " (isDefined: " + profile.isDefined() + ")");
                logAvailableStereotypes(profile);
                return profile;
            } else {
                LOGGER.warning("Resource does not contain a Profile: " + profileUri);
                return null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading profile: " + profileUri, e);
            return null;
        }
    }

    private void remapProfileResourceUri(Profile profile, Resource resource, ResourceSet resourceSet, URI originalFileUri) {
        if (resource == null || resourceSet == null || originalFileUri == null) {
            return;
        }

        URI canonicalPathmapUri = buildCanonicalProfilePathmapUri(originalFileUri);
        if (canonicalPathmapUri == null) {
            LOGGER.warning("Cannot compute canonical profile URI for: " + originalFileUri);
            return;
        }

        boolean canonicalMappingAccepted = registerUriMapping(resourceSet, canonicalPathmapUri, originalFileUri,
                "canonical profile mapping");

        // Secondary compatibility alias: profile self-declared URI, if pathmap-based.
        if (profile != null && profile.getURI() != null && !profile.getURI().isBlank()) {
            try {
                URI declaredUri = URI.createURI(profile.getURI());
                if ("pathmap".equals(declaredUri.scheme())) {
                    registerUriMapping(resourceSet, declaredUri, originalFileUri,
                            "profile-declared URI mapping");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Invalid profile declared URI: " + profile.getURI(), e);
            }
        }

        if (canonicalMappingAccepted) {
            URI previousResourceUri = resource.getURI();
            resource.setURI(canonicalPathmapUri);
            LOGGER.info("Remapped profile resource URI from " + previousResourceUri + " to " + canonicalPathmapUri);
        } else {
            LOGGER.warning("Skipped remapping profile resource URI due to mapping collision for "
                    + canonicalPathmapUri + "; keeping resource URI " + resource.getURI());
        }
    }

    private URI buildCanonicalProfilePathmapUri(URI originalFileUri) {
        if (originalFileUri == null) {
            return null;
        }

        String lastSegment = originalFileUri.lastSegment();
        if (lastSegment == null || lastSegment.isBlank()) {
            return null;
        }

        return URI.createURI(CANONICAL_PROFILE_PATHMAP_PREFIX + lastSegment);
    }

    private boolean registerUriMapping(ResourceSet resourceSet, URI sourceUri, URI targetUri, String label) {
        if (resourceSet == null || sourceUri == null || targetUri == null) {
            return false;
        }

        Map<URI, URI> uriMap = resourceSet.getURIConverter().getURIMap();
        URI existingTarget = uriMap.get(sourceUri);

        if (existingTarget == null) {
            uriMap.put(sourceUri, targetUri);
            LOGGER.info("Registered " + label + ": " + sourceUri + " -> " + targetUri);
            return true;
        }

        if (existingTarget.equals(targetUri)) {
            return true;
        }

        LOGGER.warning("URI mapping collision for " + sourceUri + " while registering " + label
                + ". Existing target: " + existingTarget + ", new target: " + targetUri
                + ". Keeping existing target.");
        return false;
    }

    /**
     * Registers the Ecore packages embedded in a profile's annotations.
     * UML2 profiles contain their Ecore definition inside EAnnotations with
     * source "http://www.eclipse.org/uml2/2.0.0/UML". These must be registered
     * in the EPackage.Registry for stereotype application to work.
     */
    private void registerProfileEcorePackages(Profile profile, ResourceSet resourceSet) {
        Map<String, EPackage> selectedByNsUri = new LinkedHashMap<>();

        for (var annotation : profile.getEAnnotations()) {
            if ("http://www.eclipse.org/uml2/2.0.0/UML".equals(annotation.getSource())) {
                for (var content : annotation.getContents()) {
                    if (content instanceof EPackage) {
                        var ePackage = (EPackage) content;
                        String nsURI = ePackage.getNsURI();
                        if (nsURI != null && !nsURI.isEmpty()) {
                            EPackage existing = selectedByNsUri.get(nsURI);
                            if (existing == null) {
                                selectedByNsUri.put(nsURI, ePackage);
                            } else {
                                EPackage preferred = choosePreferredPackage(profile, existing, ePackage);
                                if (preferred != existing) {
                                    selectedByNsUri.put(nsURI, preferred);
                                }
                                LOGGER.warning("Duplicate profile Ecore package for nsURI '" + nsURI
                                        + "' detected. Keeping package with better stereotype-name consistency.");
                            }
                        }
                    }
                }
            }
        }

        for (var entry : selectedByNsUri.entrySet()) {
            String nsURI = entry.getKey();
            EPackage ePackage = entry.getValue();

            org.eclipse.emf.ecore.EPackage.Registry.INSTANCE.put(nsURI, ePackage);
            resourceSet.getPackageRegistry().put(nsURI, ePackage);

            LOGGER.info("Registered Ecore package: " + ePackage.getName() +
                    " (nsURI: " + nsURI + ", stereotypeMatchScore: "
                    + calculateStereotypeNameMatchScore(profile, ePackage) + ")");
        }
    }

    private EPackage choosePreferredPackage(Profile profile, EPackage current, EPackage candidate) {
        int currentScore = calculateStereotypeNameMatchScore(profile, current);
        int candidateScore = calculateStereotypeNameMatchScore(profile, candidate);

        if (candidateScore > currentScore) {
            return candidate;
        }

        return current;
    }

    private int calculateStereotypeNameMatchScore(Profile profile, EPackage ePackage) {
        if (profile == null || ePackage == null) {
            return 0;
        }

        int score = 0;

        for (Stereotype stereotype : profile.getOwnedStereotypes()) {
            String stereotypeName = stereotype.getName();
            if (stereotypeName == null || stereotypeName.isBlank()) {
                continue;
            }

            EClassifier classifier = ePackage.getEClassifier(stereotypeName);
            if (classifier instanceof EClass) {
                score++;
            }
        }

        return score;
    }

    /**
     * Applies a profile to a package if not already applied.
     * 
     * @param pkg     The package to apply the profile to
     * @param profile The profile to apply
     */
    public void applyProfile(Package pkg, Profile profile) {
        if (pkg == null || profile == null) {
            LOGGER.warning("Cannot apply profile: package or profile is null");
            return;
        }

        if (!pkg.isProfileApplied(profile)) {
            try {
                // Ensure profile is defined before applying
                if (!profile.isDefined()) {
                    LOGGER.warning("Profile is not defined, attempting to use as-is: " + profile.getName());
                }

                pkg.applyProfile(profile);
                LOGGER.info("Applied profile '" + profile.getName() + "' to package '" + pkg.getName() + "'");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error applying profile: " + profile.getName(), e);
            }
        } else {
            LOGGER.info("Profile '" + profile.getName() + "' already applied to '" + pkg.getName() + "'");
        }
    }

    /**
     * Finds a stereotype by its qualified name in the given ResourceSet.
     * Use this stateless method instead of findStereotype(String).
     * 
     * @param resourceSet   The ResourceSet containing keys
     * @param qualifiedName The qualified name or simple name of the stereotype
     * @return The stereotype if found, empty otherwise
     */
    public Optional<Stereotype> findStereotype(ResourceSet resourceSet, String qualifiedName) {
        if (resourceSet == null) {
            return Optional.empty();
        }

        for (Resource resource : resourceSet.getResources()) {
            if (resource.getContents().isEmpty())
                continue;

            var root = resource.getContents().get(0);
            if (root instanceof Profile) {
                Profile profile = (Profile) root;
                for (Stereotype stereotype : profile.getOwnedStereotypes()) {
                    if (stereotype.getQualifiedName() != null &&
                            stereotype.getQualifiedName().equals(qualifiedName)) {
                        return Optional.of(stereotype);
                    }
                    // Also try matching by name only (for simpler lookups)
                    if (stereotype.getName() != null && stereotype.getName().equals(qualifiedName)) {
                        return Optional.of(stereotype);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Gets all stereotypes available from profiles loaded in the given ResourceSet.
     * Use this stateless method instead of getAvailableStereotypes().
     * 
     * @param resourceSet The ResourceSet containing the model and profiles
     * @return List of available stereotypes
     */
    public List<Stereotype> getAvailableStereotypes(ResourceSet resourceSet) {
        List<Stereotype> stereotypes = new ArrayList<>();
        if (resourceSet == null) {
            return stereotypes;
        }

        java.util.Set<String> seenStereotypes = new java.util.HashSet<>();

        for (Resource resource : resourceSet.getResources()) {
            if (resource.getContents().isEmpty()) {
                continue;
            }
            var root = resource.getContents().get(0);
            if (root instanceof Profile) {
                for (Stereotype s : ((Profile) root).getOwnedStereotypes()) {
                    String qName = s.getQualifiedName();
                    if (qName != null) {
                        if (seenStereotypes.add(qName)) {
                            stereotypes.add(s);
                        }
                    } else {
                        stereotypes.add(s);
                    }
                }
            }
        }
        return stereotypes;
    }

    /**
     * @deprecated Use the stateless getAvailableStereotypes(ResourceSet) instead.
     */
    @Deprecated
    public List<Stereotype> getAvailableStereotypes() {
        List<Stereotype> stereotypes = new ArrayList<>();
        for (Profile profile : loadedProfiles) {
            stereotypes.addAll(profile.getOwnedStereotypes());
        }
        return stereotypes;
    }

    /**
     * @deprecated Use ResourceSet iteration instead.
     */
    @Deprecated
    public List<Profile> getLoadedProfiles() {
        return Collections.unmodifiableList(loadedProfiles);
    }

    /**
     * Clears all loaded profiles.
     */
    public void clearProfiles() {
        loadedProfiles.clear();
    }

    /**
     * Finds a stereotype by its qualified name across all loaded profiles.
     * 
     * @param qualifiedName The qualified name of the stereotype (e.g.,
     *                      "uml-vm-profile::delta")
     * @return The stereotype if found, empty otherwise
     */
    public Optional<Stereotype> findStereotype(String qualifiedName) {
        for (Profile profile : loadedProfiles) {
            for (Stereotype stereotype : profile.getOwnedStereotypes()) {
                if (stereotype.getQualifiedName() != null &&
                        stereotype.getQualifiedName().equals(qualifiedName)) {
                    return Optional.of(stereotype);
                }
                // Also try matching by name only (for simpler lookups)
                if (stereotype.getName() != null && stereotype.getName().equals(qualifiedName)) {
                    return Optional.of(stereotype);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Gets stereotypes that are applicable to the given element.
     * A stereotype is applicable if it can be legally applied based on
     * its metaclass extensions.
     * 
     * @param element The element to check applicability for
     * @return List of applicable stereotypes
     */
    public List<Stereotype> getApplicableStereotypes(Element element) {
        List<Stereotype> applicable = new ArrayList<>();
        for (Stereotype stereotype : getAvailableStereotypes()) {
            try {
                if (element.isStereotypeApplicable(stereotype)) {
                    applicable.add(stereotype);
                }
            } catch (Exception e) {
                LOGGER.fine("Error checking stereotype applicability: " + stereotype.getName());
            }
        }
        return applicable;
    }

    /**
     * Logs the available stereotypes in a profile for debugging.
     */
    private void logAvailableStereotypes(Profile profile) {
        var stereotypes = profile.getOwnedStereotypes();
        if (stereotypes.isEmpty()) {
            LOGGER.info("Profile '" + profile.getName() + "' has no stereotypes");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Available stereotypes in '").append(profile.getName()).append("': [");
            for (int i = 0; i < stereotypes.size(); i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(stereotypes.get(i).getName());
            }
            sb.append("]");
            LOGGER.info(sb.toString());
        }
    }
}
