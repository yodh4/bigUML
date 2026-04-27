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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

      for (EObject object : resource.getContents()) {
         if (object == null || object.eClass() == null || object.eClass().getName() == null) {
            continue;
         }

         if (isAppliedToElement(object, element)) {
            var stereotypeName = resolveStereotypeName(object).orElse(object.eClass().getName());
            if (!stereotypeName.isBlank()) {
               names.add(stereotypeName);
            }
         }
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
