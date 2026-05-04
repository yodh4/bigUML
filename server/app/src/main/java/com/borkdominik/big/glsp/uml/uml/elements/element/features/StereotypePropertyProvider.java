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
package com.borkdominik.big.glsp.uml.uml.elements.element.features;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Stereotype;

import com.borkdominik.big.glsp.server.core.model.BGTypeProvider;
import com.borkdominik.big.glsp.server.features.property_palette.handler.BGUpdateElementPropertyAction;
import com.borkdominik.big.glsp.server.features.property_palette.model.ElementPropertyBuilder;
import com.borkdominik.big.glsp.server.features.property_palette.model.ElementPropertyItem;
import com.borkdominik.big.glsp.server.features.property_palette.provider.integrations.BGEMFElementPropertyProvider;
import com.borkdominik.big.glsp.uml.core.model.ProfileService;
import com.borkdominik.big.glsp.uml.uml.commands.UMLUpdateElementCommand;
import com.borkdominik.big.glsp.uml.uml.elements.element.StereotypeUtil;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * Property provider that shows stereotype checkboxes in the property palette.
 * Allows users to apply or remove stereotypes from UML elements via the UI.
 */
public class StereotypePropertyProvider extends BGEMFElementPropertyProvider<Element> {

    private static final Logger LOGGER = Logger.getLogger(StereotypePropertyProvider.class.getName());
    private static final String STEREOTYPE_PREFIX = "stereotype_";

    @Inject
    protected ProfileService profileService;

    @Inject
    public StereotypePropertyProvider(@Assisted final Enumerator representation,
            @Assisted final Set<BGTypeProvider> elementTypes) {
        super(representation, elementTypes, Set.of());
    }

    @Override
    public List<ElementPropertyItem> doProvide(final Element element) {
        var elementId = providerContext.idGenerator().getOrCreateId(element);
        var builder = new ElementPropertyBuilder(elementId);

        // Debug logging
        LOGGER.info("=== StereotypePropertyProvider.doProvide ===");
        LOGGER.info("Element type: " + element.getClass().getSimpleName());
        LOGGER.info("Element eClass: " + element.eClass().getName());

        var resourceSet = element.eResource().getResourceSet();

        // Log profile application status for packages
        if (element instanceof org.eclipse.uml2.uml.Package) {
            var pkg = (org.eclipse.uml2.uml.Package) element;
            LOGGER.info("Applied profiles on package: " + pkg.getAppliedProfiles().size());
        }

        var allStereotypes = profileService.getAvailableStereotypes(resourceSet);
        LOGGER.info("Available stereotypes count: " + allStereotypes.size());

        // Get the element's metaclass name for matching
        var elementMetaclass = element.eClass().getName();
        LOGGER.info("Looking for stereotypes extending: " + elementMetaclass);

        // Show stereotypes that extend this element's metaclass
        int added = 0;
        for (Stereotype stereotype : allStereotypes) {
            // Check if stereotype has a base_<Metaclass> attribute that matches this
            // element
            // This works around getAllExtendedMetaclasses() returning empty for pathmap
            // URIs
            boolean matchesMetaclass = false;
            String matchedBase = "";

            for (var attr : stereotype.getOwnedAttributes()) {
                String attrName = attr.getName();
                if (attrName != null && attrName.startsWith("base_")) {
                    String baseMetaclass = attrName.substring(5); // Remove "base_" prefix
                    matchedBase = baseMetaclass;

                    // Check if this base metaclass matches the element's type
                    if (baseMetaclass.equals(elementMetaclass) ||
                            baseMetaclass.equals("Element") ||
                            baseMetaclass.equals("NamedElement") ||
                            baseMetaclass.equals("PackageableElement") ||
                            baseMetaclass.equals("Namespace") ||
                            (baseMetaclass.equals("Classifier") &&
                                    (elementMetaclass.equals("Class") || elementMetaclass.equals("Interface")))) {
                        matchesMetaclass = true;
                        break;
                    }
                }
            }

            if (matchesMetaclass) {
                var isApplied = isStereotypeAppliedWithFallback(element, stereotype);
                String propertyId = STEREOTYPE_PREFIX + stereotype.getName();
                String label = "«" + stereotype.getName() + "»";

                builder.bool(propertyId, label, isApplied);
                LOGGER.info("Added checkbox: " + stereotype.getName() + " (base_" + matchedBase + ", applied: "
                        + isApplied + ")");
                added++;
            }
        }

        LOGGER.info("Total checkboxes added: " + added);

        return builder.items();
    }

    /**
     * Override matches to handle dynamic stereotype property IDs.
     * The framework normally checks handledProperties.contains(propertyId),
     * but stereotype IDs are dynamic, so we match by prefix instead.
     */
    @Override
    public boolean matches(final BGUpdateElementPropertyAction action) {
        return action.getPropertyId().startsWith(STEREOTYPE_PREFIX);
    }

    @Override
    public Command doHandle(final BGUpdateElementPropertyAction action, final Element element) {
        var propertyId = action.getPropertyId();
        var value = action.getValue();

        // Check if this is a stereotype property
        if (!propertyId.startsWith(STEREOTYPE_PREFIX)) {
            return null;
        }

        String stereotypeName = propertyId.substring(STEREOTYPE_PREFIX.length());
        boolean shouldApply = Boolean.parseBoolean(value);

        LOGGER.info("Handling stereotype property change: " + stereotypeName + " = " + shouldApply);

        // Find the stereotype
        // Use stateless lookup in the current ResourceSet
        var stereotypeOpt = profileService.findStereotype(element.eResource().getResourceSet(), stereotypeName);
        if (stereotypeOpt.isEmpty()) {
            LOGGER.warning("Stereotype not found: " + stereotypeName);
            return null;
        }

        var stereotype = stereotypeOpt.get();

        // Get the profile that owns this stereotype
        var profile = stereotype.getProfile();
        if (profile == null) {
            LOGGER.warning("Stereotype has no profile: " + stereotypeName);
            return null;
        }

        // Only take action if the state is changing
        var currentlyApplied = isStereotypeAppliedWithFallback(element, stereotype);

        if (shouldApply == currentlyApplied) {
            return null;
        }

        // Create command to apply or remove stereotype
        var argument = UMLUpdateElementCommand.Argument
                .<Element>updateElementArgumentBuilder()
                .consumer(e -> {
                    if (shouldApply) {
                        // First, ensure the profile is applied to the element's package
                        org.eclipse.uml2.uml.Package containingPackage = e.getNearestPackage();
                        LOGGER.info("Element's nearest package: "
                                + (containingPackage != null ? containingPackage.getName() : "null"));

                        if (containingPackage != null) {
                            if (!containingPackage.isProfileApplied(profile)) {
                                try {
                                    containingPackage.applyProfile(profile);
                                    LOGGER.info("Applied profile '" + profile.getName() + "' to package '"
                                            + containingPackage.getName() + "'");
                                } catch (Exception ex) {
                                    LOGGER.info("Could not apply profile via UML2 API: " + ex.getMessage());
                                }
                            }
                        }

                        // Try UML2 API first
                        try {
                            if (e.isStereotypeApplicable(stereotype)) {
                                e.applyStereotype(stereotype);
                                LOGGER.info("Applied stereotype '" + stereotypeName + "' via UML2 API");
                                return;
                            }
                        } catch (Exception ex) {
                            LOGGER.info("UML2 API failed: " + ex.getMessage());
                        }

                        // Fallback: manual EMF application
                        LOGGER.info("Using manual EMF application for stereotype '" + stereotypeName + "'");
                        applyStereotypeManually(e, stereotype, profile);
                    } else {
                        // Try UML2 unapply first
                        try {
                            if (e.isStereotypeApplied(stereotype)) {
                                e.unapplyStereotype(stereotype);
                                LOGGER.info("Removed stereotype '" + stereotypeName + "' via UML2 API");
                                return;
                            }
                        } catch (Exception ex) {
                            LOGGER.info("UML2 unapply failed: " + ex.getMessage());
                        }

                        // Fallback: manual removal
                        removeStereotypeManually(e, stereotype);
                    }
                })
                .build();

        return new UMLUpdateElementCommand<>(context, modelState.getSemanticModel(), element, argument);
    }

    /**
     * Manually applies a stereotype to an element by creating the Ecore EObject
     * directly from the profile's Ecore definition.
     */
    private void applyStereotypeManually(Element element, Stereotype stereotype, Profile profile) {
        try {
            var stereotypeEClassOpt = StereotypeUtil.resolveStereotypeApplicationEClass(profile, stereotype);
            if (stereotypeEClassOpt.isEmpty()) {
                LOGGER.warning("No EClass found for stereotype '" + stereotype.getName() + "' in profile definition");
                return;
            }

            org.eclipse.emf.ecore.EClass stereotypeEClass = stereotypeEClassOpt.get();
            LOGGER.info("Resolved stereotype application EClass for '" + stereotype.getName()
                    + "' as '" + stereotypeEClass.getName() + "'");

            var stereotypeEPackage = stereotypeEClass.getEPackage();
            if (stereotypeEPackage == null || stereotypeEPackage.getEFactoryInstance() == null) {
                LOGGER.warning("No EFactory available for stereotype EClass '" + stereotypeEClass.getName() + "'");
                return;
            }

            // Create an instance of the stereotype's EClass
            org.eclipse.emf.ecore.EObject stereotypeApp = stereotypeEPackage.getEFactoryInstance().create(stereotypeEClass);

            // Set the base_* reference to the element
            String baseName = "base_" + element.eClass().getName();
            org.eclipse.emf.ecore.EStructuralFeature baseFeature = stereotypeEClass.getEStructuralFeature(baseName);
            if (baseFeature != null) {
                stereotypeApp.eSet(baseFeature, element);
            } else {
                LOGGER.warning("No base feature '" + baseName + "' found on stereotype EClass");
                return;
            }

            // Add the stereotype application to the element's resource
            org.eclipse.emf.ecore.resource.Resource resource = element.eResource();
            if (resource != null) {
                resource.getContents().add(stereotypeApp);
                StereotypeUtil.invalidateCache(); // immediately invalidate — safety net
                LOGGER.info("Successfully applied stereotype '" + stereotype.getName()
                        + "' manually via EMF to element");
            } else {
                LOGGER.warning("Element has no resource, cannot add stereotype application");
            }
        } catch (Exception ex) {
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to apply stereotype manually: " + ex.getMessage(), ex);
        }
    }

    /**
     * Manually removes a stereotype application from an element.
     */
    private void removeStereotypeManually(Element element, Stereotype stereotype) {
        try {
            org.eclipse.emf.ecore.resource.Resource resource = element.eResource();
            if (resource == null) {
                return;
            }

            // Find and remove the stereotype application EObject
            var iterator = resource.getContents().iterator();
            while (iterator.hasNext()) {
                var obj = iterator.next();
                if (StereotypeUtil.isStereotypeApplicationOf(obj, stereotype)) {
                    // Check if this application refers to our element
                    String baseName = "base_" + element.eClass().getName();
                    org.eclipse.emf.ecore.EStructuralFeature baseFeature = obj.eClass()
                            .getEStructuralFeature(baseName);
                    if (baseFeature != null && obj.eGet(baseFeature) == element) {
                        iterator.remove();
                        StereotypeUtil.invalidateCache(); // immediately invalidate — safety net
                        LOGGER.info("Removed stereotype '" + stereotype.getName() + "' manually from element");
                        return;
                    }
                }
            }
            LOGGER.info("No stereotype application found to remove for '" + stereotype.getName() + "'");
        } catch (Exception ex) {
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to remove stereotype manually: " + ex.getMessage(), ex);
        }
    }

    private boolean isStereotypeAppliedWithFallback(final Element element, final Stereotype stereotype) {
        if (element == null || stereotype == null || stereotype.getName() == null) {
            return false;
        }

        try {
            if (element.isStereotypeApplied(stereotype)) {
                return true;
            }
        } catch (Exception ignored) {
            // fallback below handles standalone/manual cases
        }

        return StereotypeUtil.getAppliedStereotypeNames(element).contains(stereotype.getName());
    }
}
