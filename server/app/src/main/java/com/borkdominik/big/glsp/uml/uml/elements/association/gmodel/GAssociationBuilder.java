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
package com.borkdominik.big.glsp.uml.uml.elements.association.gmodel;

import java.util.List;
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.glsp.graph.GEdge;
import org.eclipse.glsp.graph.builder.impl.GEdgePlacementBuilder;
import org.eclipse.glsp.graph.util.GConstants;
import org.eclipse.uml2.uml.AggregationKind;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Property;

import com.borkdominik.big.glsp.server.core.constants.BGCoreCSS;
import com.borkdominik.big.glsp.server.sdk.cdk.GCModelContext;
import com.borkdominik.big.glsp.server.sdk.cdk.base.GCProvider;
import com.borkdominik.big.glsp.server.sdk.cdk.gmodel.GCModelList;
import com.borkdominik.big.glsp.server.sdk.cdk.utils.GCNone;
import com.borkdominik.big.glsp.server.sdk.ui.builder.GCEdgeBuilder;
import com.borkdominik.big.glsp.server.sdk.ui.components.label.GCLabel;
import com.borkdominik.big.glsp.server.sdk.ui.components.label.GCNameLabel;
import com.borkdominik.big.glsp.server.sdk.utils.StreamUtils;
import com.borkdominik.big.glsp.uml.uml.UMLTypes;
import com.borkdominik.big.glsp.uml.uml.elements.association.utils.AggregationKindUtil;
import com.borkdominik.big.glsp.uml.uml.elements.element.StereotypeUtil;
import com.borkdominik.big.glsp.uml.uml.elements.multiplicity_element.MultiplicityUtil;
import com.borkdominik.big.glsp.uml.uml.elements.property.gmodel.suffix.PropertyMultiplicityLabelSuffix;
import com.borkdominik.big.glsp.uml.unotation.Representation;

public class GAssociationBuilder<TOrigin extends Association> extends GCEdgeBuilder<TOrigin> {

   public GAssociationBuilder(final GCModelContext context, final TOrigin origin, final String type) {
      super(context, origin, type);
   }

   @Override
   public EObject source() {
      var source = sourcePropertyElement();
      return source != null ? source : origin;
   }

   public Property sourceProperty() {
      var memberEnds = origin.getMemberEnds();
      if (memberEnds.isEmpty()) {
         return null;
      }
      return memberEnds.get(0);
   }

   public Element sourcePropertyElement() {
      var source = sourceProperty();
      var target = targetProperty();
      return resolvePropertyElement(source, target);
   }

   @Override
   public EObject target() {
      var target = targetPropertyElement();
      return target != null ? target : origin;
   }

   public Property targetProperty() {
      var memberEnds = origin.getMemberEnds();
      if (memberEnds.size() < 2) {
         return null;
      }
      return memberEnds.get(1);
   }

   public Element targetPropertyElement() {
      var target = targetProperty();
      var source = sourceProperty();
      return resolvePropertyElement(target, source);
   }

   protected Element resolvePropertyElement(final Property property, final Property opposite) {
      if (property == null) {
         return null;
      }

      if (property.getOwner() instanceof Association) {
         if (opposite != null && opposite.getType() != null) {
            return opposite.getType();
         }
         return property.getType();
      }

      return property.getOwner();
   }

   @Override
   protected List<String> getRootGModelCss() {
      var css = StreamUtils.concat(super.getDefaultCss(), List.<String>of());
      if (!supportsDynamicArrow()) {
         return StreamUtils.concat(css, List.of(BGCoreCSS.Marker.TENT.end()));
      }

      var sourceProperty = sourceProperty();
      var targetProperty = targetProperty();
      if (sourceProperty == null || targetProperty == null) {
         return css;
      }

      var sourceNavigable = isNavigable(sourceProperty);
      var targetNavigable = isNavigable(targetProperty);

      if (sourceNavigable) {
         css.add(BGCoreCSS.Marker.TENT.end());
      }

      if (targetNavigable) {
         css.add(BGCoreCSS.Marker.TENT.start());
      }

      return css;
   }

   protected boolean supportsDynamicArrow() {
      if (context.representation() != Representation.CLASS) {
         return false;
      }

      var sourceProperty = sourceProperty();
      var targetProperty = targetProperty();
      if (sourceProperty == null || targetProperty == null) {
         return false;
      }

      return sourceProperty.getAggregation() == AggregationKind.NONE_LITERAL
         && targetProperty.getAggregation() == AggregationKind.NONE_LITERAL;
   }

   protected boolean isNavigable(final Property property) {
      if (property == null) {
         return false;
      }

      if (property.getOwner() instanceof Association association) {
         return association.getNavigableOwnedEnds().contains(property);
      }

      return property.isNavigable();
   }

   @Override
   protected List<GCProvider> createComponentChildren(final GEdge gmodelRoot, final GCModelList<?, ?> componentRoot) {
      var source = sourceProperty();
      var target = targetProperty();

      if (source == null || target == null) {
         return List.of(createStereotypeLabel(), createName(componentRoot));
      }

      return StreamUtils.concat(
         List.of(createStereotypeLabel(), createName(componentRoot)),
         createMemberEnd(gmodelRoot, source, 0.9d, GConstants.EdgeSide.BOTTOM, GConstants.EdgeSide.TOP),
         createMemberEnd(gmodelRoot, target, 0.1d, GConstants.EdgeSide.TOP, GConstants.EdgeSide.BOTTOM));
   }

   protected GCProvider createStereotypeLabel() {
      var stereotypeNames = StereotypeUtil.getAppliedStereotypeNames(origin);
      if (stereotypeNames.isEmpty()) {
         return new GCNone(context, origin);
      }
      return createCenteredLabel(StereotypeUtil.formatStereotypeLabel(stereotypeNames));
   }

   protected GCProvider createName(final GCModelList<?, ?> root) {
      if (origin.getName() != null && !origin.getName().isBlank()) {
         var options = GCNameLabel.Options.builder()
            .label(origin.getName())
            .edgePlacement(new GEdgePlacementBuilder()
               .side(GConstants.EdgeSide.TOP)
               .position(0.5d)
               .offset(10d)
               .rotate(true)
               .build())
            .build();

         return new GCNameLabel(context, origin, options);
      }

      return new GCNone(context, origin);
   }

   protected List<GCProvider> createMemberEnd(final GEdge edge, final Property memberEnd, final double position,
      final String labelSide, final String multiplicitySide) {

      var memberEndId = context.idGenerator.getOrCreateId(memberEnd);

      var name = GCNameLabel.Options.builder()
         .label(memberEnd.getName())
         .clearCss()
         .css(BGCoreCSS.TEXT_HIGHLIGHT)
         .edgePlacement(new GEdgePlacementBuilder()
            .side(labelSide)
            .position(position)
            .rotate(true)
            .offset(10d)
            .build())
         .build();

      var multiplicity = GCNameLabel.Options.builder()
         .label(MultiplicityUtil.getMultiplicity(memberEnd))
         .clearCss()
         .id(Optional.of(context.suffix.appendTo(PropertyMultiplicityLabelSuffix.SUFFIX, memberEndId)))
         .type(UMLTypes.PROPERTY_MULTIPLICITY.prefix(context.representation()))
         .edgePlacement(new GEdgePlacementBuilder()
            .side(multiplicitySide)
            .position(position)
            .offset(10d)
            .rotate(true)
            .build())
         .build();

      var marker = AggregationKindUtil.from(memberEnd.getAggregation());
      edge.getCssClasses().add(position < 0.5d ? marker.start() : marker.end());

      return List.of(
         new GCNameLabel(context, memberEnd, name),
         new GCLabel(context, memberEnd, multiplicity));
   }

}
