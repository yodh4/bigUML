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
package com.borkdominik.big.glsp.uml.uml.elements.association.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.AttributeOwner;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.AggregationKind;

import com.borkdominik.big.glsp.server.core.model.BGTypeProvider;
import com.borkdominik.big.glsp.server.features.property_palette.handler.BGUpdateElementPropertyAction;
import com.borkdominik.big.glsp.server.features.property_palette.model.ElementChoicePropertyItem;
import com.borkdominik.big.glsp.server.features.property_palette.model.ElementPropertyBuilder;
import com.borkdominik.big.glsp.server.features.property_palette.model.ElementPropertyItem;
import com.borkdominik.big.glsp.server.features.property_palette.provider.integrations.BGEMFElementPropertyProvider;
import com.borkdominik.big.glsp.uml.uml.UMLTypes;
import com.borkdominik.big.glsp.uml.uml.commands.UMLUpdateElementCommand;
import com.borkdominik.big.glsp.uml.uml.elements.element.VisibilityKindUtils;
import com.borkdominik.big.glsp.uml.uml.elements.multiplicity_element.MultiplicityElementPropertyProvider;
import com.borkdominik.big.glsp.uml.uml.elements.multiplicity_element.MultiplicityUtil;
import com.borkdominik.big.glsp.uml.uml.elements.named_element.NamedElementPropertyProvider;
import com.borkdominik.big.glsp.uml.unotation.Representation;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class AssociationPropertyProvider extends BGEMFElementPropertyProvider<Association> {

   public static final String SOURCE_OWNER = "sourceOwner";
   public static final String TARGET_OWNER = "targetOwner";
   public static final String SOURCE_NAVIGABLE = "sourceNavigable";
   public static final String TARGET_NAVIGABLE = "targetNavigable";

   protected static final String OWNER_CLASSIFIER = "classifier";
   protected static final String OWNER_ASSOCIATION = "association";

   @Inject
   public AssociationPropertyProvider(@Assisted final Enumerator representation,
      @Assisted final Set<BGTypeProvider> elementTypes) {
      super(representation, elementTypes, Set.of(SOURCE_OWNER, TARGET_OWNER, SOURCE_NAVIGABLE, TARGET_NAVIGABLE));
   }

   @Override
   public List<ElementPropertyItem> doProvide(final Association element) {
      var memberEnds = element.getMemberEnds();
      if (memberEnds.size() < 2) {
         return List.of();
      }

      var sourceProperty = memberEnds.get(0);
      var targetProperty = memberEnds.get(1);

      var properties = new ArrayList<ElementPropertyItem>();
      if (supportsOwnerNavigableControls(element)) {
         var associationId = idGenerator.getOrCreateId(element);

         var sourceOwner = sourceProperty.getOwner() instanceof Association ? OWNER_ASSOCIATION : OWNER_CLASSIFIER;
         var targetOwner = targetProperty.getOwner() instanceof Association ? OWNER_ASSOCIATION : OWNER_CLASSIFIER;

         properties.addAll(new ElementPropertyBuilder(associationId)
            .choice(SOURCE_OWNER, "Source Owner", ownerChoices(), sourceOwner)
            .bool(SOURCE_NAVIGABLE, "Source Navigable", isNavigable(element, sourceProperty))
            .choice(TARGET_OWNER, "Target Owner", ownerChoices(), targetOwner)
            .bool(TARGET_NAVIGABLE, "Target Navigable", isNavigable(element, targetProperty))
            .items());
      }

      properties.addAll(provideIfConfigured(Set.of(UMLTypes.PROPERTY), () -> {
         var sourcePropertyId = idGenerator.getOrCreateId(sourceProperty);

         return new ElementPropertyBuilder(sourcePropertyId)
            .text(NamedElementPropertyProvider.NAME, "Source Name", sourceProperty.getName())
            .choice(
               NamedElementPropertyProvider.VISIBILITY_KIND,
               "Source Visibility",
               VisibilityKindUtils.asChoices(),
               sourceProperty.getVisibility().getLiteral())
            .choice(MultiplicityElementPropertyProvider.MULTIPLICITY, "Source Multiplicity",
               multiplicityChoices(),
               MultiplicityUtil.getMultiplicity(sourceProperty))
            .items();

      }));
      properties.addAll(provideIfConfigured(Set.of(UMLTypes.PROPERTY), () -> {
         var targetPropertyId = idGenerator.getOrCreateId(targetProperty);

         return new ElementPropertyBuilder(targetPropertyId)
            .text(NamedElementPropertyProvider.NAME, "Target Name", targetProperty.getName())
            .choice(
               NamedElementPropertyProvider.VISIBILITY_KIND,
               "Target Visibility",
               VisibilityKindUtils.asChoices(),
               targetProperty.getVisibility().getLiteral())
            .choice(MultiplicityElementPropertyProvider.MULTIPLICITY, "Target Multiplicity",
               multiplicityChoices(),
               MultiplicityUtil.getMultiplicity(targetProperty))
            .items();
      }));
      return properties;
   }

   @Override
   public Command doHandle(final BGUpdateElementPropertyAction action, final Association element) {
      var value = action.getValue();
      var argument = UMLUpdateElementCommand.Argument
         .<Association> updateElementArgumentBuilder()
         .consumer(association -> {
            if (!supportsOwnerNavigableControls(association)) {
               return;
            }

            var memberEnds = association.getMemberEnds();
            if (memberEnds.size() < 2) {
               return;
            }

            var sourceProperty = memberEnds.get(0);
            var targetProperty = memberEnds.get(1);

            switch (action.getPropertyId()) {
               case SOURCE_OWNER:
                  setOwner(association, sourceProperty, targetProperty, value);
                  break;
               case TARGET_OWNER:
                  setOwner(association, targetProperty, sourceProperty, value);
                  break;
               case SOURCE_NAVIGABLE:
                  setNavigable(association, sourceProperty, Boolean.parseBoolean(value));
                  break;
               case TARGET_NAVIGABLE:
                  setNavigable(association, targetProperty, Boolean.parseBoolean(value));
                  break;
               default:
                  break;
            }
         })
         .build();

      return new UMLUpdateElementCommand<>(context, modelState.getSemanticModel(), element, argument);
   }

   protected boolean supportsOwnerNavigableControls(final Association association) {
      if (representation != Representation.CLASS) {
         return false;
      }

      var memberEnds = association.getMemberEnds();
      if (memberEnds.size() < 2) {
         return false;
      }

      return memberEnds.get(0).getAggregation() == AggregationKind.NONE_LITERAL
         && memberEnds.get(1).getAggregation() == AggregationKind.NONE_LITERAL;
   }

   protected List<ElementChoicePropertyItem.Choice> ownerChoices() {
      return List.of(
         ElementChoicePropertyItem.Choice.builder().label("Classifier").value(OWNER_CLASSIFIER).build(),
         ElementChoicePropertyItem.Choice.builder().label("Association").value(OWNER_ASSOCIATION).build());
   }

   protected List<ElementChoicePropertyItem.Choice> multiplicityChoices() {
      return List.of(
         ElementChoicePropertyItem.Choice.builder().label("0..*").value("0..*").build(),
         ElementChoicePropertyItem.Choice.builder().label("1..*").value("1..*").build(),
         ElementChoicePropertyItem.Choice.builder().label("0..1").value("0..1").build(),
         ElementChoicePropertyItem.Choice.builder().label("1").value("1").build());
   }

   protected void setOwner(final Association association, final Property end, final Property opposite,
      final String ownerValue) {
      if (OWNER_CLASSIFIER.equals(ownerValue)) {
         var owner = resolveOwnerForEnd(opposite);
         if (owner != null) {
            moveEndToClassifier(association, end, owner);
            end.setIsNavigable(true);
         }
      } else if (OWNER_ASSOCIATION.equals(ownerValue)) {
         var navigable = isNavigable(association, end);
         moveEndToAssociation(association, end);
         setAssociationOwnedNavigability(association, end, navigable);
      }
   }

   protected void setNavigable(final Association association, final Property end, final boolean navigable) {
      if (end.getOwner() instanceof Association) {
         setAssociationOwnedNavigability(association, end, navigable);
         return;
      }

      if (navigable) {
         end.setIsNavigable(true);
         return;
      }

      moveEndToAssociation(association, end);
      setAssociationOwnedNavigability(association, end, false);
   }

   protected AttributeOwner resolveOwnerForEnd(final Property opposite) {
      var type = opposite.getType();
      if (type instanceof AttributeOwner owner) {
         return owner;
      }
      return null;
   }

   protected boolean isNavigable(final Association association, final Property end) {
      if (end.getOwner() instanceof Association) {
         return association.getNavigableOwnedEnds().contains(end);
      }

      return end.isNavigable();
   }

   protected void moveEndToClassifier(final Association association, final Property end, final AttributeOwner owner) {
      removeFromCurrentOwner(association, end);
      if (!owner.getOwnedAttributes().contains(end)) {
         owner.getOwnedAttributes().add(end);
      }
      association.getNavigableOwnedEnds().remove(end);
   }

   protected void moveEndToAssociation(final Association association, final Property end) {
      removeFromCurrentOwner(association, end);
      if (!association.getOwnedEnds().contains(end)) {
         association.getOwnedEnds().add(end);
      }
   }

   protected void removeFromCurrentOwner(final Association association, final Property end) {
      var owner = end.getOwner();
      if (owner instanceof AttributeOwner attributeOwner) {
         attributeOwner.getOwnedAttributes().remove(end);
      } else if (owner instanceof Association assocOwner) {
         assocOwner.getOwnedEnds().remove(end);
         assocOwner.getNavigableOwnedEnds().remove(end);
      }

      association.getNavigableOwnedEnds().remove(end);
   }

   protected void setAssociationOwnedNavigability(final Association association, final Property end,
      final boolean navigable) {
      if (navigable) {
         if (!association.getNavigableOwnedEnds().contains(end)) {
            association.getNavigableOwnedEnds().add(end);
         }
      } else {
         association.getNavigableOwnedEnds().remove(end);
      }
   }
}
