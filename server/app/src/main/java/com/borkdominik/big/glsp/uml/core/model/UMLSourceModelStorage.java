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

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.glsp.server.actions.SaveModelAction;
import org.eclipse.glsp.server.emf.EMFIdGenerator;
import org.eclipse.glsp.server.emf.model.notation.NotationFactory;
import org.eclipse.glsp.server.features.core.model.RequestModelAction;
import org.eclipse.glsp.server.types.GLSPServerException;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;

import com.borkdominik.big.glsp.server.core.handler.action.new_file.BGRequestNewFileAction;
import com.borkdominik.big.glsp.server.core.model.integrations.BGEMFSourceModelStorage;
import com.borkdominik.big.glsp.uml.unotation.UMLDiagram;
import com.borkdominik.big.glsp.uml.unotation.UnotationFactory;
import com.borkdominik.big.glsp.uml.unotation.UnotationPackage;
import com.google.inject.Inject;

public class UMLSourceModelStorage extends BGEMFSourceModelStorage {

   private static final Logger LOGGER = Logger.getLogger(UMLSourceModelStorage.class.getName());

   @Inject
   protected EMFIdGenerator idGenerator;

   @Inject
   protected UMLModelMigrator migrator;

   @Inject
   protected ProfileService profileService;

   @Override
   protected ResourceSet setupResourceSet(final ResourceSet resourceSet) {
      super.setupResourceSet(resourceSet);
      resourceSet.getPackageRegistry().put(UMLPackage.eINSTANCE.getNsURI(), UMLPackage.eINSTANCE);
      resourceSet.getPackageRegistry().put(UnotationPackage.eINSTANCE.getNsURI(), UnotationPackage.eINSTANCE);
      return resourceSet;
   }

   @Override
   protected void doLoadSourceModel(ResourceSet resourceSet, URI sourceURI, RequestModelAction action) {
      // IMPORTANT: Load profiles BEFORE loading the model.
      // Profile Ecore packages must be registered in the ResourceSet before EMF
      // tries to deserialize stereotype application EObjects from the .uml file.
      // Without this, reopening a file with applied stereotypes fails with
      // PackageNotFoundException for the profile's Ecore namespace URI.
      loadAndRegisterProfiles(resourceSet, sourceURI);

      super.doLoadSourceModel(resourceSet, sourceURI, action);
   }

   @Override
   protected void loadNotationModel(ResourceSet resourceSet, URI sourceURI, RequestModelAction action) {
      // Migrate the notation model file if necessary
      migrator.migrateNotationModel(resourceSet, deriveNotationModelURI(sourceURI), action);

      // profiles are now loaded in doLoadSourceModel

      super.loadNotationModel(resourceSet, sourceURI, action);
   }

   /**
    * Discovers and loads UML profiles from the model directory.
    * This registers profile Ecore packages so EMF can deserialize stereotype
    * application EObjects. Must be called BEFORE loading the model.
    */
   protected void loadAndRegisterProfiles(ResourceSet resourceSet, URI sourceURI) {
      LOGGER.info("Attempting to load and register profiles for model URI: " + sourceURI);
      try {
         var profileUris = profileService.discoverProfileUris(sourceURI);

         if (profileUris.isEmpty()) {
            LOGGER.info("No profiles found to register.");
            return;
         }

         synchronized (resourceSet) {
            for (var profileUri : profileUris) {
               LOGGER.info("Loading profile from: " + profileUri);
               // Load the profile (which also defines it and registers Ecore packages)
               // Profile application to specific packages is done on-demand
               // in StereotypePropertyProvider when stereotypes are applied
               var profile = profileService.loadProfile(profileUri, resourceSet);
               if (profile != null) {
                  LOGGER.info("Successfully loaded and registered profile: " + profile.getName());
               } else {
                  LOGGER.warning("Failed to load profile from: " + profileUri);
               }
            }
         }

      } catch (Exception e) {
         LOGGER.log(java.util.logging.Level.SEVERE, "Error during profile loading/registration", e);
      }
   }

   /**
    * Override save to filter out non-file resources.
    * When profiles are loaded, EMF may resolve pathmap:// URIs
    * (e.g., pathmap://UML_METAMODELS/UML.metamodel.uml) and add them
    * to the ResourceSet. Attempting to save these causes MalformedURLException.
    * We only save resources with file:// URIs.
    */
   @Override
   public void saveSourceModel(final SaveModelAction action) {
      var resourceSet = modelState.getResourceSet();
      for (Resource resource : resourceSet.getResources().toArray(new Resource[0])) {
         URI uri = resource.getURI();

         // Only save resources with file:// scheme (skip pathmap://, profile resources,
         // etc.)
         if (uri == null || !uri.isFile()) {
            LOGGER.fine("Skipping save for non-file resource: " + uri);
            continue;
         }

         // Also skip .profile.uml files — they are read-only definitions
         if (uri.toString().endsWith(".profile.uml")) {
            LOGGER.fine("Skipping save for profile resource: " + uri);
            continue;
         }

         try {
            if (isUmlResource(uri)) {
               var saveOptions = new HashMap<String, Object>();
               saveOptions.put(XMLResource.OPTION_SCHEMA_LOCATION, Boolean.TRUE);
               resource.save(saveOptions);
            } else {
               resource.save(null);
            }
         } catch (IOException e) {
            throw new GLSPServerException("Could not save model to file: " + uri, e);
         }
      }
   }

   private boolean isUmlResource(final URI uri) {
      if (uri == null) {
         return false;
      }

      var uriText = uri.toString();
      return uriText.endsWith(".uml") && !uriText.endsWith(".profile.uml");
   }

   @Override
   protected URI deriveNotationModelURI(final URI sourceURI) {
      return sourceURI.trimFileExtension().appendFileExtension("unotation");
   }

   @Override
   protected void doCreateSourceModel(final ResourceSet resourceSet, final URI resourceURI,
         final BGRequestNewFileAction action) {
      var packageRegistry = resourceSet.getPackageRegistry();

      packageRegistry.entrySet().stream()
            .filter(entry -> {
               var value = entry.getValue();
               return value instanceof UMLPackage || value instanceof UnotationPackage;
            })
            .forEach((entry) -> {
               var ePackage = (EPackage) entry.getValue();
               doCreateResource(resourceSet, ePackage, resourceURI);
            });

      var umlFile = resourceURI.appendFileExtension(UMLPackage.eINSTANCE.getNsPrefix());
      var umlResource = resourceSet.getResource(umlFile, false);
      var unotationFile = resourceURI.appendFileExtension(UnotationPackage.eINSTANCE.getNsPrefix());
      var unotationResource = resourceSet.getResource(unotationFile, false);

      var model = (Model) umlResource.getAllContents().next();
      var diagram = (UMLDiagram) unotationResource.getAllContents().next();
      var semanticProxy = NotationFactory.eINSTANCE
            .createSemanticElementReference();
      semanticProxy.setElementId(idGenerator.getOrCreateId(model));
      diagram.setSemanticElement(semanticProxy);
      diagram.setDiagramType(action.getDiagramType());

      try {
         unotationResource.save(null);
      } catch (IOException e) {
         throw new GLSPServerException("Failed to save file", e);
      }
   }

   @Override
   protected EObject doCreateResourceContent(final EPackage ePackage) {
      if (ePackage.equals(UMLPackage.eINSTANCE)) {
         return UMLFactory.eINSTANCE.createModel();
      } else if (ePackage.equals(UnotationPackage.eINSTANCE)) {
         return UnotationFactory.eINSTANCE.createUMLDiagram();
      }

      return super.doCreateResourceContent(ePackage);
   }
}
