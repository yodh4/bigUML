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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
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

    private final List<Profile> loadedProfiles = new ArrayList<>();

    /**
     * Discovers profile files in the same directory as the model file.
     * 
     * @param modelUri URI of the UML model file
     * @return List of URIs for discovered profile files
     */
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

        // Find all *.profile.uml files in the directory
        File[] profileFiles = modelDir.listFiles((dir, name) -> name.endsWith(PROFILE_EXTENSION));

        if (profileFiles != null) {
            for (File profileFile : profileFiles) {
                URI profileUri = URI.createFileURI(profileFile.getAbsolutePath());
                profileUris.add(profileUri);
                LOGGER.info("Discovered profile: " + profileFile.getName());
            }
        }

        return profileUris;
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
                loadedProfiles.add(profile);
                LOGGER.info("Loaded profile: " + profile.getName() + " from " + profileUri);
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
     * Gets all stereotypes available from loaded profiles.
     * 
     * @return List of available stereotypes
     */
    public List<Stereotype> getAvailableStereotypes() {
        List<Stereotype> stereotypes = new ArrayList<>();
        for (Profile profile : loadedProfiles) {
            stereotypes.addAll(profile.getOwnedStereotypes());
        }
        return stereotypes;
    }

    /**
     * Gets the list of currently loaded profiles.
     * 
     * @return Unmodifiable list of loaded profiles
     */
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
