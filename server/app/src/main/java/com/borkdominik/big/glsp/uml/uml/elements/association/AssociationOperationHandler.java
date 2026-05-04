/********************************************************************************
 * Copyright (c) 2023 borkdominik and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0, or the MIT License which is
 * available at https://opensource.org/licenses/MIT.
 *
 * SPDX-License-Identifier: EPL-2.0 OR MIT
 ********************************************************************************/
package com.borkdominik.big.glsp.uml.uml.elements.association;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.glsp.server.operations.CreateEdgeOperation;
import org.eclipse.glsp.server.operations.DeleteOperation;
import org.eclipse.glsp.server.operations.ReconnectEdgeOperation;
import org.eclipse.uml2.uml.AggregationKind;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.AttributeOwner;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Type;

import com.borkdominik.big.glsp.server.core.commands.BGRecordingRunnableCommand;
import com.borkdominik.big.glsp.server.core.commands.emf.notation.BGEMFDeleteNotationCommand;
import com.borkdominik.big.glsp.server.core.commands.semantic.BGCreateEdgeSemanticCommand;
import com.borkdominik.big.glsp.server.core.handler.operation.delete.BGDeleteHandler;
import com.borkdominik.big.glsp.server.core.handler.operation.reconnect_edge.BGReconnectEdgeHandler;
import com.borkdominik.big.glsp.server.core.model.BGTypeProvider;
import com.borkdominik.big.glsp.server.elements.handler.operations.integrations.BGEMFEdgeOperationHandler;
import com.borkdominik.big.glsp.uml.uml.UMLTypes;
import com.borkdominik.big.glsp.uml.uml.commands.UMLCreateEdgeCommand;
import com.borkdominik.big.glsp.uml.unotation.Representation;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class AssociationOperationHandler extends BGEMFEdgeOperationHandler<Association, Type, Type>
   implements BGDeleteHandler, BGReconnectEdgeHandler {

   @Inject
   public AssociationOperationHandler(@Assisted final Enumerator representation,
      @Assisted final Set<BGTypeProvider> elementTypes) {
      super(representation, elementTypes);
   }

   @Override
   protected BGCreateEdgeSemanticCommand<Association, Type, Type, ?> createSemanticCommand(
      final CreateEdgeOperation operation, final Type source, final Type target) {
      var elementTypeId = operation.getElementTypeId();
      var isDirectedPlainAssociation = isClassRepresentation() && UMLTypes.ASSOCIATION.isSame(representation, elementTypeId);
      var argument = UMLCreateEdgeCommand.Argument
         .<Association, Type, Type> createEdgeArgumentBuilder()
         .supplier((s, t) -> {
            var isPlainAssociation = UMLTypes.ASSOCIATION.isSame(representation, elementTypeId);
            var targetName = t.getName();
            var sourceName = s.getName();

            if (isPlainAssociation) {
               if (targetName != null) {
                  targetName = targetName.toLowerCase();
               }
               if (sourceName != null) {
                  sourceName = sourceName.toLowerCase();
               }
            }

            var type = AggregationKind.NONE_LITERAL;
            if (UMLTypes.AGGREGATION.isSame(representation, elementTypeId)) {
               type = AggregationKind.SHARED_LITERAL;
            } else if (UMLTypes.COMPOSITION.isSame(representation, elementTypeId)) {
               type = AggregationKind.COMPOSITE_LITERAL;
            }

            var createdAssociation = s.createAssociation(true,
               type,
               targetName,
               1, 1,
               t,
               !isDirectedPlainAssociation,
               AggregationKind.NONE_LITERAL,
               sourceName,
               1, 1);

            if (isDirectedPlainAssociation) {
               normalizeDirectedDefault(createdAssociation, s, t);
            }

            return createdAssociation;
         })
         .build();

      return new UMLCreateEdgeCommand<>(commandContext, source, target, argument);
   }

   @Override
   public Optional<Command> handleDelete(final DeleteOperation operation, final EObject object) {
      var semanticElement = modelState.getElementIndex().getSemanticOrThrow(object, Association.class);
      var snapshotMemberEnds = new ArrayList<>(semanticElement.getMemberEnds());

      var command = new CompoundCommand();
      command.append(new BGRecordingRunnableCommand(modelState.getSemanticModel(), () -> {
         if (isAlive(semanticElement)) {
            semanticElement.destroy();
         }

         for (var end : snapshotMemberEnds) {
            cleanupSurvivingEnd(end);
         }
      }));

      command.append(new BGEMFDeleteNotationCommand(commandContext, semanticElement));

      return Optional.of(command);
   }

   @Override
   public Optional<Command> handleReconnect(final ReconnectEdgeOperation operation, final EObject element) {
      var elementId = operation.getEdgeElementId();
      var sourceId = operation.getSourceElementId();
      var targetId = operation.getTargetElementId();

      var semanticElement = modelState.getElementIndex().getOrThrow(elementId, Association.class);
      var source = modelState.getElementIndex().getOrThrow(sourceId, Type.class);
      var target = modelState.getElementIndex().getOrThrow(targetId, Type.class);

      return Optional.of(new BGRecordingRunnableCommand(modelState.getSemanticModel(), () -> {
         var memberEnds = semanticElement.getMemberEnds();
         if (memberEnds.size() < 2) {
            return;
         }

         var sourceProperty = memberEnds.get(0);
         var targetProperty = memberEnds.get(1);

         var isSourceAssociationOwned = sourceProperty.getOwner() instanceof Association;
         var isTargetAssociationOwned = targetProperty.getOwner() instanceof Association;

         var sourceAssociationOwnedNavigable = isAssociationOwnedNavigable(semanticElement, sourceProperty);
         var targetAssociationOwnedNavigable = isAssociationOwnedNavigable(semanticElement, targetProperty);

         sourceProperty.setType(target);
         targetProperty.setType(source);

         if (isSourceAssociationOwned) {
            moveEndToAssociation(semanticElement, sourceProperty);
            setAssociationOwnedNavigability(semanticElement, sourceProperty, sourceAssociationOwnedNavigable);
         } else {
            var newSourceOwner = asAttributeOwner(source);
            if (newSourceOwner != null) {
               moveEndToClassifier(semanticElement, sourceProperty, newSourceOwner);
               sourceProperty.setIsNavigable(true);
            } else {
               moveEndToAssociation(semanticElement, sourceProperty);
               setAssociationOwnedNavigability(semanticElement, sourceProperty, sourceAssociationOwnedNavigable);
            }
         }

         if (isTargetAssociationOwned) {
            moveEndToAssociation(semanticElement, targetProperty);
            setAssociationOwnedNavigability(semanticElement, targetProperty, targetAssociationOwnedNavigable);
         } else {
            var newTargetOwner = asAttributeOwner(target);
            if (newTargetOwner != null) {
               moveEndToClassifier(semanticElement, targetProperty, newTargetOwner);
               targetProperty.setIsNavigable(true);
            } else {
               moveEndToAssociation(semanticElement, targetProperty);
               setAssociationOwnedNavigability(semanticElement, targetProperty, targetAssociationOwnedNavigable);
            }
         }

      }));
   }

   protected boolean isClassRepresentation() {
      return representation == Representation.CLASS;
   }

   protected void normalizeDirectedDefault(final Association association, final Type source, final Type target) {
      var memberEnds = association.getMemberEnds();
      if (memberEnds.size() < 2) {
         return;
      }

      var sourceEnd = memberEnds.get(0);
      var targetEnd = memberEnds.get(1);

      sourceEnd.setType(target);
      targetEnd.setType(source);

      var sourceOwner = asAttributeOwner(source);
      if (sourceOwner != null) {
         moveEndToClassifier(association, sourceEnd, sourceOwner);
         sourceEnd.setIsNavigable(true);
      }

      moveEndToAssociation(association, targetEnd);
      setAssociationOwnedNavigability(association, targetEnd, false);
   }

   protected AttributeOwner asAttributeOwner(final Type type) {
      if (type instanceof AttributeOwner owner) {
         return owner;
      }
      return null;
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

   protected boolean isAssociationOwnedNavigable(final Association association, final Property end) {
      if (!(end.getOwner() instanceof Association)) {
         return end.isNavigable();
      }
      return association.getNavigableOwnedEnds().contains(end);
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

   protected void cleanupSurvivingEnd(final Property end) {
      try {
         if (!isAlive(end) && end.eContainer() == null) {
            return;
         }

         removeEndFromOwner(end);

         if (isAlive(end)) {
            EcoreUtil.delete(end, true);
         }
      } catch (RuntimeException ignored) {
         // best effort cleanup to avoid blocking deletion of other ends
      }
   }

   protected void removeEndFromOwner(final Property end) {
      var owner = end.getOwner();
      if (owner instanceof AttributeOwner attributeOwner) {
         if (attributeOwner.getOwnedAttributes().contains(end)) {
            attributeOwner.getOwnedAttributes().remove(end);
         }
      } else if (owner instanceof Association associationOwner) {
         associationOwner.getOwnedEnds().remove(end);
         associationOwner.getNavigableOwnedEnds().remove(end);
      }

      var association = end.getAssociation();
      if (association != null) {
         association.getOwnedEnds().remove(end);
         association.getNavigableOwnedEnds().remove(end);
      }
   }

   protected boolean isAlive(final EObject element) {
      return element != null && !element.eIsProxy() && element.eResource() != null && element.eContainer() != null;
   }

}
