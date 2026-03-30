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
package com.borkdominik.big.glsp.uml.uml.elements.component.gmodel;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.glsp.graph.GNode;
import org.eclipse.glsp.graph.util.GConstants;
import org.eclipse.uml2.uml.Component;

import com.borkdominik.big.glsp.server.core.constants.BGQuotationMark;
import com.borkdominik.big.glsp.server.sdk.cdk.GCModelContext;
import com.borkdominik.big.glsp.server.sdk.cdk.base.GCProvider;
import com.borkdominik.big.glsp.server.sdk.cdk.gmodel.GCModelList;
import com.borkdominik.big.glsp.server.sdk.gmodel.BCCompartmentBuilder;
import com.borkdominik.big.glsp.server.sdk.gmodel.BCLayoutOptions;
import com.borkdominik.big.glsp.server.sdk.ui.builder.GCNodeBuilder;
import com.borkdominik.big.glsp.server.sdk.ui.components.list.GCList;
import com.borkdominik.big.glsp.uml.uml.elements.attribute_owner.GCAttributeOwner;
import com.borkdominik.big.glsp.uml.uml.elements.element.StereotypeUtil;
import com.borkdominik.big.glsp.uml.uml.elements.named_element.GCNamedElement;
import com.borkdominik.big.glsp.uml.uml.elements.operation_owner.GCOperationOwner;
import com.borkdominik.big.glsp.uml.unotation.Representation;

public final class GComponentBuilder<TOrigin extends Component> extends GCNodeBuilder<TOrigin> {
   protected final GCOperationOwner<TOrigin> operationOwner;
   protected final GCAttributeOwner<TOrigin> attributeOwner;

   public GComponentBuilder(final GCModelContext context, final TOrigin origin, final String type) {
      super(context, origin, type);
      this.operationOwner = new GCOperationOwner<>(context, origin);
      this.attributeOwner = new GCAttributeOwner<>(context, origin);
   }

   @Override
   protected GCModelList<?, ?> createRootComponent(final GNode gmodelRoot) {
      var componentRoot = new GCModelList<>(context, origin,
         new BCCompartmentBuilder<>(origin, context)
            .withRootComponentLayout()
            .addLayoutOptions(new BCLayoutOptions().hAlign(GConstants.HAlign.CENTER).vGap(5))
            .build());

      componentRoot.addAll(createComponentChildren(gmodelRoot, componentRoot));

      return componentRoot;
   }

   @Override
   protected List<GCProvider> createComponentChildren(final GNode gmodelRoot, final GCModelList<?, ?> root) {
      return List.of(createHeader(root), createBody(root));
   }

   protected GCProvider createHeader(final GCModelList<?, ?> root) {
      var namedElementOptions = GCNamedElement.Options.builder()
         .container(root)
         .prefix(BGQuotationMark.quoteDoubleAngle("Subsystem"));

      var stereotypeNames = StereotypeUtil.getAppliedStereotypeNames(origin);
      if (!stereotypeNames.isEmpty()) {
         namedElementOptions.prefix(StereotypeUtil.formatStereotypeLabel(stereotypeNames));
      }

      return new GCNamedElement<>(context, origin, namedElementOptions.build());
   }

   protected GCProvider createBody(final GCModelList<?, ?> root) {
      if (context.representation() == Representation.CLASS) {
         return createClassBody();
      }

      return createUseCaseBody();
   }

   protected GCProvider createClassBody() {
      var options = GCList.Options.builder()
         .dividerBeforeInserts(true)
         .build();
      var list = new GCList(context, origin, options);

      list.add(attributeOwner);
      list.add(operationOwner);

      return list;
   }

   protected GCProvider createUseCaseBody() {
      var list = new GCModelList<>(context, origin, new BCCompartmentBuilder<>(origin, context)
         .withFreeformLayout()
         .build());

      list.addAllGModels(origin.getOwnedUseCases().stream()
         .map(e -> context.mapHandler.handle(e))
         .collect(Collectors.toList()));
      list.addAllGModels(origin.getOwnedUseCases().stream()
         .map(e -> context.mapHandler.handleSiblings(e))
         .flatMap(Collection::stream)
         .collect(Collectors.toList()));

      return list;
   }

}
