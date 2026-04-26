/********************************************************************************
 * Copyright (c) 2021-2022 borkdominik and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0, or the MIT License which is
 * available at https://opensource.org/licenses/MIT.
 *
 * SPDX-License-Identifier: EPL-2.0 OR MIT
 ********************************************************************************/
package com.borkdominik.big.glsp.uml.uml.elements.package_.gmodel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.glsp.graph.GModelElement;
import org.eclipse.glsp.graph.GNode;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.Relationship;

import com.borkdominik.big.glsp.server.core.model.BGTypeProvider;
import com.borkdominik.big.glsp.server.elements.gmodel.BGEMFElementGModelMapper;
import com.borkdominik.big.glsp.uml.uml.UMLTypes;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public final class PackageGModelMapper extends BGEMFElementGModelMapper<Package, GNode> {

   @Inject
   public PackageGModelMapper(@Assisted final Enumerator representation,
      @Assisted final Set<BGTypeProvider> elementTypes) {
      super(representation, elementTypes);
   }

   @Override
   public GNode map(final Package source) {
      return new GPackageBuilder<>(gcmodelContext, source, UMLTypes.PACKAGE.prefix(representation)).buildGModel();
   }

   @Override
   public List<GModelElement> mapSiblings(final Package source) {
      var siblings = new ArrayList<GModelElement>();

      siblings.addAll(mapHandler.handle(source.getElementImports()));
      siblings.addAll(mapHandler.handle(source.getPackageImports()));
      siblings.addAll(mapHandler.handle(source.getPackageMerges()));
      siblings.addAll(mapHandler.handle(packageOwnedRelationships(source)));

      return deduplicateById(siblings);
   }

   protected List<PackageableElement> packageOwnedRelationships(final Package source) {
      return source.getPackagedElements().stream()
         .filter(this::isPackageOwnedRelationship)
         .collect(Collectors.toList());
   }

   protected boolean isPackageOwnedRelationship(final PackageableElement element) {
      return element instanceof Relationship;
   }

   protected List<GModelElement> deduplicateById(final List<GModelElement> elements) {
      var withIds = new LinkedHashSet<String>();
      var deduplicated = new ArrayList<GModelElement>();
      for (var element : elements) {
         var id = element.getId();
         if (id == null) {
            deduplicated.add(element);
            continue;
         }

         if (withIds.add(id)) {
            deduplicated.add(element);
         }
      }
      return deduplicated;
   }

}
