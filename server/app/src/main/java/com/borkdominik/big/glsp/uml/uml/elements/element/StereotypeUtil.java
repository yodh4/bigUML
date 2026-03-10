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
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.uml2.uml.Element;

import com.borkdominik.big.glsp.server.core.constants.BGQuotationMark;

public final class StereotypeUtil {
   private static final String BASE_FEATURE_PREFIX = "base_";

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
            var stereotypeName = object.eClass().getName();
            if (!stereotypeName.isBlank()) {
               names.add(stereotypeName);
            }
         }
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
