/********************************************************************************
 * Copyright (c) 2026 borkdominik and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0, or the MIT License which is
 * available at https://opensource.org/licenses/MIT.
 *
 * SPDX-License-Identifier: EPL-2.0 OR MIT
 ********************************************************************************/
package com.borkdominik.big.glsp.uml.uml.elements.element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Profile;
import org.eclipse.uml2.uml.Stereotype;

import com.borkdominik.big.glsp.server.core.constants.BGQuotationMark;

public final class StereotypeUtil {
   private static final String BASE_FEATURE_PREFIX = "base_";
   private static final String UML_ANNOTATION_SOURCE = "http://www.eclipse.org/uml2/2.0.0/UML";

   // ---------------------------------------------------------------------------
   // Stereotype index cache — replaces the O(N×M) full-resource scan
   // ---------------------------------------------------------------------------

   /**
    * Cached index of manually-applied stereotypes, built by scanning
    * resource.getContents() once and mapping each target Element to
    * its applied stereotype names.
    */
   private static final class StereotypeIndex {
      final org.eclipse.emf.ecore.resource.Resource resource; // identity reference
      final int contentSize;                                   // snapshot of resource.getContents().size()
      final Map<Element, List<String>> elementToNames;         // the index

      StereotypeIndex(org.eclipse.emf.ecore.resource.Resource resource, int contentSize,
            Map<Element, List<String>> elementToNames) {
         this.resource = resource;
         this.contentSize = contentSize;
         this.elementToNames = elementToNames;
      }

      /**
       * Returns true if this index is still valid for the given resource.
       * The index is valid when the resource reference is the same instance
       * AND the top-level contents count has not changed.
       * <p>
       * Adding or removing a stereotype application always changes
       * resource.getContents().size(), so this is a reliable invalidation signal.
       */
      boolean isValidFor(org.eclipse.emf.ecore.resource.Resource res) {
         return this.resource == res
               && this.contentSize == res.getContents().size();
      }
   }

   /** The current cached index. Replaced lazily when stale. */
   private static StereotypeIndex cachedIndex = null;

   /**
    * Builds the stereotype index for the given resource.
    * Scans all top-level EObjects in resource.getContents() once,
    * identifies stereotype application EObjects (those with base_ features),
    * and maps each target Element to its applied stereotype names.
    */
   private static StereotypeIndex buildIndex(final org.eclipse.emf.ecore.resource.Resource resource) {
      var index = new HashMap<Element, List<String>>();

      for (EObject object : resource.getContents()) {
         if (object == null || object.eClass() == null || object.eClass().getName() == null) {
            continue;
         }

         // Check each base_ feature to find the target Element(s)
         for (EStructuralFeature feature : object.eClass().getEAllStructuralFeatures()) {
            var featureName = feature.getName();
            if (featureName == null || !featureName.startsWith(BASE_FEATURE_PREFIX)) {
               continue;
            }

            try {
               var target = object.eGet(feature);
               if (target instanceof Element element) {
                  var stereotypeName = resolveStereotypeName(object)
                        .orElse(object.eClass().getName());
                  if (stereotypeName != null && !stereotypeName.isBlank()) {
                     index.computeIfAbsent(element, k -> new ArrayList<>())
                          .add(stereotypeName);
                  }
               }
            } catch (Exception ignored) {
               // ignore unreadable dynamic features
            }
         }
      }

      return new StereotypeIndex(resource, resource.getContents().size(), index);
   }

   /**
    * Returns the cached index for the given resource, rebuilding it if stale.
    */
   private static StereotypeIndex getOrBuildIndex(final org.eclipse.emf.ecore.resource.Resource resource) {
      var current = cachedIndex;
      if (current != null && current.isValidFor(resource)) {
         return current;
      }

      var newIndex = buildIndex(resource);
      cachedIndex = newIndex;
      return newIndex;
   }

   /**
    * Explicitly invalidates the stereotype index cache.
    * Call this after directly modifying stereotype applications
    * (e.g., in StereotypePropertyProvider.applyStereotypeManually).
    * <p>
    * Note: The cache is also automatically invalidated when
    * resource.getContents().size() changes, so this is a safety net
    * that guarantees immediate invalidation.
    */
   public static void invalidateCache() {
      cachedIndex = null;
   }

   // ---------------------------------------------------------------------------

   private StereotypeUtil() {
   }

   public static List<String> getAppliedStereotypeNames(final Element element) {
      var names = new LinkedHashSet<String>();

      if (element == null) {
         return List.of();
      }

      addNativeAppliedStereotypes(element, names);
      addManualAppliedStereotypes(element, names);

      return new ArrayList<>(names);
   }

   public static String formatStereotypeLabel(final List<String> names) {
      if (names == null || names.isEmpty()) {
         return "";
      }

      return BGQuotationMark.quoteDoubleAngle(String.join(", ", names));
   }

   private static void addNativeAppliedStereotypes(final Element element, final LinkedHashSet<String> names) {
      try {
         element.getAppliedStereotypes().forEach(st -> {
            if (st != null && st.getName() != null && !st.getName().isBlank()) {
               names.add(st.getName());
            }
         });
      } catch (Exception ignored) {
         // standalone behavior can throw or miss manually-applied stereotypes
      }
   }

   private static void addManualAppliedStereotypes(final Element element, final LinkedHashSet<String> names) {
      var resource = element.eResource();
      if (resource == null) {
         return;
      }

      // O(1) amortized: look up the pre-built index instead of scanning all contents
      var index = getOrBuildIndex(resource);
      var cachedNames = index.elementToNames.get(element);
      if (cachedNames != null) {
         names.addAll(cachedNames);
      }
   }

   public static Optional<EClass> resolveStereotypeApplicationEClass(final Profile profile,
         final Stereotype stereotype) {
      if (profile == null || stereotype == null) {
         return Optional.empty();
      }

      var candidatePackages = getCandidateDefinitionPackages(profile);
      if (candidatePackages.isEmpty()) {
         return Optional.empty();
      }

      String stereotypeName = stereotype.getName();
      if (stereotypeName != null && !stereotypeName.isBlank()) {
         for (EPackage ePackage : candidatePackages) {
            EClassifier byName = ePackage.getEClassifier(stereotypeName);
            if (byName instanceof EClass eClass) {
               return Optional.of(eClass);
            }
         }
      }

      for (EPackage ePackage : candidatePackages) {
         for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EClass eClass && matchesStereotypeByUmlAnnotation(eClass, stereotype)) {
               return Optional.of(eClass);
            }
         }
      }

      return Optional.empty();
   }

   public static boolean isStereotypeApplicationOf(final EObject stereotypeApplication,
         final Stereotype stereotype) {
      if (stereotypeApplication == null || stereotypeApplication.eClass() == null || stereotype == null) {
         return false;
      }

      return matchesStereotypeByUmlAnnotation(stereotypeApplication.eClass(), stereotype);
   }

   public static Optional<String> resolveStereotypeName(final EObject stereotypeApplication) {
      if (stereotypeApplication == null || stereotypeApplication.eClass() == null) {
         return Optional.empty();
      }

      var annotation = stereotypeApplication.eClass().getEAnnotation(UML_ANNOTATION_SOURCE);
      if (annotation == null) {
         return Optional.empty();
      }

      for (EObject reference : annotation.getReferences()) {
         var resolved = resolveReference(reference, stereotypeApplication);
         if (resolved instanceof Stereotype stereotype
               && stereotype.getName() != null
               && !stereotype.getName().isBlank()) {
            return Optional.of(stereotype.getName());
         }
      }

      return Optional.empty();
   }

   public static boolean matchesStereotypeByUmlAnnotation(final EClass eClass, final Stereotype stereotype) {
      if (eClass == null || stereotype == null) {
         return false;
      }

      for (EAnnotation annotation : eClass.getEAnnotations()) {
         if (!UML_ANNOTATION_SOURCE.equals(annotation.getSource())) {
            continue;
         }

         for (EObject reference : annotation.getReferences()) {
            if (isSameStereotypeReference(reference, stereotype, eClass)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean isSameStereotypeReference(final EObject reference,
         final Stereotype stereotype,
         final EObject context) {
      var resolved = resolveReference(reference, context);

      if (sameUri(reference, stereotype) || sameUri(resolved, stereotype)) {
         return true;
      }

      if (resolved == stereotype) {
         return true;
      }

      if (resolved instanceof Stereotype resolvedStereotype) {
         var resolvedUri = EcoreUtil.getURI(resolvedStereotype);
         var stereotypeUri = EcoreUtil.getURI(stereotype);
         if (resolvedUri != null && resolvedUri.equals(stereotypeUri)) {
            return true;
         }

         return resolvedStereotype.getQualifiedName() != null
               && resolvedStereotype.getQualifiedName().equals(stereotype.getQualifiedName());
      }

      return false;
   }

   private static boolean sameUri(final EObject a, final EObject b) {
      if (a == null || b == null) {
         return false;
      }

      var aUri = EcoreUtil.getURI(a);
      var bUri = EcoreUtil.getURI(b);

      return aUri != null && aUri.equals(bUri);
   }

   private static List<EPackage> getCandidateDefinitionPackages(final Profile profile) {
      var ordered = new LinkedHashMap<String, EPackage>();

      EPackage definition = profile.getDefinition();
      if (definition != null) {
         ordered.put(stablePackageKey(definition), definition);
      }

      for (var annotation : profile.getEAnnotations()) {
         if (!UML_ANNOTATION_SOURCE.equals(annotation.getSource())) {
            continue;
         }

         for (EObject content : annotation.getContents()) {
            if (content instanceof EPackage ePackage) {
               ordered.putIfAbsent(stablePackageKey(ePackage), ePackage);
            }
         }
      }

      return new ArrayList<>(ordered.values());
   }

   private static String stablePackageKey(final EPackage ePackage) {
      var id = EcoreUtil.getID(ePackage);
      if (id != null && !id.isBlank()) {
         return id;
      }

      var uri = EcoreUtil.getURI(ePackage);
      if (uri != null) {
         return uri.toString();
      }

      return Integer.toHexString(System.identityHashCode(ePackage));
   }

   private static EObject resolveReference(final EObject reference, final EObject context) {
      if (reference == null) {
         return null;
      }

      if (!reference.eIsProxy()) {
         return reference;
      }

      try {
         var resolved = EcoreUtil.resolve(reference, context);
         return resolved != null ? resolved : reference;
      } catch (Exception ignored) {
         return reference;
      }
   }

   private static boolean isAppliedToElement(final EObject stereotypeApplication, final Element target) {
      for (EStructuralFeature feature : stereotypeApplication.eClass().getEAllStructuralFeatures()) {
         var featureName = feature.getName();
         if (featureName == null || !featureName.startsWith(BASE_FEATURE_PREFIX)) {
            continue;
         }

         try {
            if (stereotypeApplication.eGet(feature) == target) {
               return true;
            }
         } catch (Exception ignored) {
            // ignore unreadable dynamic features
         }
      }
      return false;
   }
}
