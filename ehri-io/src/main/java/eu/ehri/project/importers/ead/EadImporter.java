/*
 * Copyright 2015 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen), King's College London,
 * Georg-August-Universitaet Goettingen Stiftung Oeffentlichen Rechts
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.ehri.project.importers.ead;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.ImportLog;
import eu.ehri.project.importers.base.SaxXmlImporter;
import eu.ehri.project.models.AccessPoint;
import eu.ehri.project.models.AccessPointType;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.base.AbstractUnit;
import eu.ehri.project.models.base.Accessible;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.persistence.BundleManager;
import eu.ehri.project.persistence.Mutation;
import eu.ehri.project.persistence.Serializer;
import eu.ehri.project.api.Api;
import eu.ehri.project.api.ApiFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Import EAD for a given repository into the database. Due to the laxness of the EAD standard this is a fairly complex
 * procedure. An EAD a single entity at the highest level of description or multiple top-level entities, with or without
 * a hierarchical structure describing their child items. This means that we need to recursively descend through the
 * archdesc and c,c01-12 levels.
 * <p>
 * TODO: Extensive cleanups, optimisation, and rationalisation.
 */
public class EadImporter extends SaxXmlImporter {

    private static final Logger logger = LoggerFactory.getLogger(EadImporter.class);
    //the EadImporter can import ead as DocumentaryUnits, the default, or overwrite those and create VirtualUnits instead.
    private final EntityClass unitEntity = EntityClass.DOCUMENTARY_UNIT;
    private final Serializer mergeSerializer;
    public static final String ACCESS_POINT = "AccessPoint";

    /**
     * Construct an EadImporter object.
     *
     * @param graph           the framed graph
     * @param permissionScope the permission scope
     * @param log             the log
     */
    public EadImporter(FramedGraph<?> graph, PermissionScope permissionScope, Actioner actioner, ImportLog log) {
        super(graph, permissionScope, actioner, log);
        mergeSerializer = new Serializer.Builder(graph).dependentOnly().build();
    }

    /**
     * Import a single archdesc or c01-12 item, keeping a reference to the hierarchical depth.
     *
     * @param itemData The raw data map
     * @param idPath   The identifiers of parent documents,
     *                 not including those of the overall permission scope
     * @throws ValidationError when the itemData does not contain an identifier for the unit or...
     */
    @Override
    public AbstractUnit importItem(Map<String, Object> itemData, List<String> idPath)
            throws ValidationError {

        BundleManager persister = getPersister(idPath);

        Bundle description = getDescription(itemData);

        // extractDocumentaryUnit does not throw ValidationError on missing ID
        Bundle unit = new Bundle(unitEntity, extractDocumentaryUnit(itemData));

        // Check for missing identifier, throw an exception when there is no ID.
        if (unit.getDataValue(Ontology.IDENTIFIER_KEY) == null) {
            throw new ValidationError(unit, Ontology.IDENTIFIER_KEY,
                    "Missing identifier " + Ontology.IDENTIFIER_KEY);
        }
        logger.debug("Imported item: {}", itemData.get("name"));

        Mutation<DocumentaryUnit> mutation =
                persister.createOrUpdate(mergeWithPreviousAndSave(unit,
                        description, idPath), DocumentaryUnit.class);
        DocumentaryUnit frame = mutation.getNode();

        // Set the repository/item relationship
        if (idPath.isEmpty() && mutation.created()) {
            EntityClass scopeType = manager.getEntityClass(permissionScope);
            if (scopeType.equals(EntityClass.REPOSITORY)) {
                Repository repository = framedGraph.frame(permissionScope.asVertex(), Repository.class);
                frame.setRepository(repository);
                frame.setPermissionScope(repository);
            } else if (scopeType.equals(unitEntity)) {
                DocumentaryUnit parent = framedGraph.frame(permissionScope.asVertex(), DocumentaryUnit.class);
                parent.addChild(frame);
                frame.setPermissionScope(parent);
            } else {
                logger.error("Unknown scope type for documentary unit: {}", scopeType);
            }
        }
        handleCallbacks(mutation);
        logger.debug("============== {} state: {}", frame.getId(), mutation.getState());
        if (mutation.created()) {
            solveUndeterminedRelationships(frame);
        }
        return frame;
    }

    /**
     * Extract the documentary unit description bundle from the raw map data.
     * <p>
     * Note: the itemData map is mutable and should be considered an out parameter.
     *
     * @param itemData the raw data map
     * @return a description bundle
     * @throws ValidationError
     */
    protected Bundle getDescription(Map<String, Object> itemData) throws ValidationError {
        List<Map<String, Object>> extractedDates = extractDates(itemData);
        replaceDates(itemData, extractedDates);

        Map<String, Object> raw = extractUnitDescription(itemData, EntityClass.DOCUMENTARY_UNIT_DESCRIPTION);

        Bundle.Builder descBuilder = Bundle.Builder.withClass(EntityClass.DOCUMENTARY_UNIT_DESCRIPTION)
                .addData(raw);

        // Add dates and descriptions to the bundle since they're @Dependent
        // relations.
        for (Map<String, Object> dpb : extractedDates) {
            descBuilder.addRelation(Ontology.ENTITY_HAS_DATE, new Bundle(EntityClass.DATE_PERIOD, dpb));
        }

        for (Map<String, Object> rel : extractRelations(itemData)) {//, (String) unit.getErrors().get(Identifiable.IDENTIFIER_KEY)
            logger.debug("relation found: {}", rel.get(Ontology.NAME_KEY));
            descBuilder.addRelation(Ontology.HAS_ACCESS_POINT, new Bundle(EntityClass.ACCESS_POINT, rel));
        }

        for (Map<String, Object> dpb : extractMaintenanceEvent(itemData)) {
            logger.debug("maintenance event found {}", dpb);
            //dates in maintenanceEvents are no DatePeriods, they are not something to search on
            descBuilder.addRelation(Ontology.HAS_MAINTENANCE_EVENT,
                    new Bundle(EntityClass.MAINTENANCE_EVENT, dpb));
        }

        Map<String, Object> unknowns = extractUnknownProperties(itemData);
        if (!unknowns.isEmpty()) {
            StringBuilder unknownProperties = new StringBuilder();
            for (String u : unknowns.keySet()) {
                unknownProperties.append(u);
            }
            logger.debug("Unknown Properties found: {}", unknownProperties);
            descBuilder.addRelation(Ontology.HAS_UNKNOWN_PROPERTY,
                    new Bundle(EntityClass.UNKNOWN_PROPERTY, unknowns));
        }

        // Set the description identifier same as the source file ID,
        // which together with the lang code should form a unique
        // identifier within the item
        descBuilder.addDataValue(Ontology.IDENTIFIER_KEY, raw.get(Ontology.SOURCEFILE_KEY));
        return descBuilder.build();
    }

    /**
     * Finds any bundle in the graph with the same ObjectIdentifier.
     * If there is no bundle with this identifier, it is created.
     * If it exists and a Description in the given language exists from the same source file,
     * the description is replaced. If the description is from another source, it is added to the
     * bundle's descriptions.
     *
     * @param unit       the DocumentaryUnit to be saved
     * @param descBundle the documentsDescription to replace any previous ones with this language
     * @param idPath     the ID path of this bundle (will be relative to the ID path of the permission scope)
     * @return A bundle with description relationships merged.
     * @throws ValidationError
     */
    protected Bundle mergeWithPreviousAndSave(Bundle unit, Bundle descBundle, List<String> idPath) throws ValidationError {
        final String languageOfDesc = descBundle.getDataValue(Ontology.LANGUAGE_OF_DESCRIPTION);
        final String thisSourceFileId = descBundle.getDataValue(Ontology.SOURCEFILE_KEY);

        logger.debug("merging: descBundle's language = {}, sourceFileId = {}",
                languageOfDesc, thisSourceFileId);
        /*
         * for some reason, the idpath from the permissionscope does not contain the parent documentary unit.
         * TODO: so for now, it is added manually
         */
        List<String> itemIdPath = Lists.newArrayList(getPermissionScope().idPath());
        itemIdPath.addAll(idPath);

        Bundle unitWithIds = unit.generateIds(itemIdPath);
        logger.debug("merging: docUnit's graph id = {}", unitWithIds.getId());
        // If the bundle exists, we merge
        if (manager.exists(unitWithIds.getId())) {
            try {
                // read the current item’s bundle
                Bundle oldBundle = mergeSerializer
                        .vertexToBundle(manager.getVertex(unitWithIds.getId()));

                // filter out dependents that a) are descriptions, b) have the same language/code,
                // and c) have the same source file ID
                Bundle.Filter filter = (relationLabel, bundle) -> {
                    String lang = bundle.getDataValue(Ontology.LANGUAGE);
                    String oldSourceFileId = bundle.getDataValue(Ontology.SOURCEFILE_KEY);
                    return relationLabel.equals(Ontology.DESCRIPTION_FOR_ENTITY)
                            && bundle.getType().equals(EntityClass.DOCUMENTARY_UNIT_DESCRIPTION)
                            && (lang != null && lang.equals(languageOfDesc))
                            && (oldSourceFileId != null && oldSourceFileId.equals(thisSourceFileId));
                };
                Bundle filtered = oldBundle.filterRelations(filter);

                return unitWithIds.withRelations(filtered.getRelations())
                        .withRelation(Ontology.DESCRIPTION_FOR_ENTITY, descBundle);
            } catch (SerializationError ex) {
                throw new ValidationError(unit, "serialization error", ex.getMessage());
            } catch (ItemNotFound ex) {
                throw new ValidationError(unit, "item not found exception", ex.getMessage());
            }
        } else { // else we create a new bundle.
            return unit.withRelation(Ontology.DESCRIPTION_FOR_ENTITY, descBundle);
        }
    }

    /**
     * Subclasses can override this method to cater to their special needs for UndeterminedRelationships
     * by default, it expects something like this in the original EAD:
     * <p>
     * <pre>
     * {@code
     * <persname source="terezin-victims" authfilenumber="PERSON.ITI.1514982">Kien,
     * Leonhard (* 11.5.1886)</persname>
     * }
     * </pre>
     * <p>
     * it works in unison with the extractRelations() method.
     *
     * @param unit the current unit
     * @throws ValidationError
     */
    protected void solveUndeterminedRelationships(DocumentaryUnit unit) throws ValidationError {
        // Try to resolve the undetermined relationships
        // we can only create the annotations after the DocumentaryUnit
        // and its Description have been added to the graph,
        // so they have IDs.
        Api api = ApiFactory.noLogging(framedGraph, actioner.as(UserProfile.class));
        Bundle linkBundle = new Bundle(EntityClass.LINK)
                .withDataValue(Ontology.LINK_HAS_DESCRIPTION, RESOLVED_LINK_DESC);

        for (Description desc : unit.getDescriptions()) {
            // Put the set of relationships into a HashSet to remove duplicates.
            for (AccessPoint rel : Sets.newHashSet(desc.getAccessPoints())) {
                // the wp2 undetermined relationship that can be resolved have a 'cvoc' and a 'concept' attribute.
                // they need to be found in the vocabularies that are in the graph
                if (rel.getPropertyKeys().contains("cvoc")) {
                    String vocab = rel.getProperty("cvoc");
                    String conceptId = rel.getProperty("concept");
                    if (conceptId == null) {
                        conceptId = rel.getProperty("target");
                    }
                    logger.debug("cvoc: {}, concept: {}", vocab, conceptId);
                    try {
                        Vocabulary vocabulary = manager.getEntity(vocab, Vocabulary.class);
                        for (Concept concept : vocabulary.getConcepts()) {
                            logger.debug("********************* {} {}", concept.getId(), concept.getIdentifier());
                            if (concept.getIdentifier().equalsIgnoreCase(conceptId)) {
                                try {
                                    // TODO: Fix link type here...
                                    Bundle data = linkBundle
                                            .withDataValue(Ontology.LINK_HAS_TYPE, "associative");
                                    Link link = api.create(data, Link.class);
                                    unit.addLink(link);
                                    concept.addLink(link);
                                    link.addLinkBody(rel);
                                    logger.debug("link created between {} and {}", conceptId, concept.getId());
                                } catch (PermissionDenied | DeserializationError ex) {
                                    logger.error(ex.getMessage());
                                }
                            }
                        }
                    } catch (ItemNotFound ex) {
                        logger.error("Vocabulary with id {} not found: {}", vocab, ex.getMessage());
                    }
                } else {
                    logger.debug("no cvoc found");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Iterable<Map<String, Object>> extractRelations(Map<String, Object> data) {
        String rel = "relation";
        List<Map<String, Object>> list = Lists.newArrayList();
        for (String key : data.keySet()) {
            if (key.equals(rel)) {
                //name identifier
                for (Map<String, Object> origRelation : (List<Map<String, Object>>) data.get(key)) {
                    Map<String, Object> relationNode = Maps.newHashMap();
                    if (origRelation.containsKey("type")) {
                        //try to find the original identifier
                        relationNode.put(LINK_TARGET, origRelation.get("concept"));
                        //try to find the original name
                        relationNode.put(Ontology.NAME_KEY, origRelation.get("name"));
                        relationNode.put("cvoc", origRelation.get("cvoc"));
                        relationNode.put(Ontology.ACCESS_POINT_TYPE, origRelation.get("type"));
                    } else {
                        relationNode.put(Ontology.NAME_KEY, origRelation.get(rel));
                    }
                    if (!relationNode.containsKey(Ontology.ACCESS_POINT_TYPE)) {
                        logger.debug("relationNode without type: {}", relationNode.get(Ontology.NAME_KEY));
                        relationNode.put(Ontology.ACCESS_POINT_TYPE, AccessPointType.corporateBody);
                    }
                    list.add(relationNode);
                }
            } else if (key.endsWith(ACCESS_POINT)) {

                if (data.get(key) instanceof List) {
                    //type, targetUrl, targetName, notes
                    for (Map<String, Object> origRelation : (List<Map<String, Object>>) data.get(key)) {
                        if (origRelation.isEmpty()) {
                            break;
                        }
                        Map<String, Object> relationNode = Maps.newHashMap();
                        for (String eventkey : origRelation.keySet()) {
                            if (eventkey.endsWith(ACCESS_POINT)) {
                                relationNode.put(Ontology.ACCESS_POINT_TYPE,
                                        eventkey.substring(0, eventkey.indexOf(ACCESS_POINT)));
                                relationNode.put(Ontology.NAME_KEY, origRelation.get(eventkey));
                            } else {
                                relationNode.put(eventkey, origRelation.get(eventkey));
                            }
                        }
                        if (!relationNode.containsKey(Ontology.ACCESS_POINT_TYPE)) {
                            relationNode.put(Ontology.ACCESS_POINT_TYPE, AccessPointType.corporateBody);
                        }
                        //if no name is given, it was apparently an empty <controlaccess> tag?
                        if (relationNode.containsKey(Ontology.NAME_KEY)) {
                            list.add(relationNode);
                        }
                    }
                } else {
                    Map<String, Object> relationNode = Maps.newHashMap();
                    relationNode.put(Ontology.ACCESS_POINT_TYPE,
                            key.substring(0, key.indexOf(ACCESS_POINT)));
                    relationNode.put(Ontology.NAME_KEY, data.get(key));
                    list.add(relationNode);
                }
            }
        }
        return list;
    }

    /**
     * Creates a Map containing properties of a Documentary Unit.
     * These properties are the unit's identifiers.
     *
     * @param itemData Map of all extracted information
     * @return a Map representing a Documentary Unit node
     * @throws ValidationError
     */
    @Override
    protected Map<String, Object> extractDocumentaryUnit(Map<String, Object> itemData) throws ValidationError {
        Map<String, Object> unit = Maps.newHashMap();
        if (itemData.get(OBJECT_IDENTIFIER) != null) {
            unit.put(Ontology.IDENTIFIER_KEY, itemData.get(OBJECT_IDENTIFIER));
        }
        if (itemData.get(Ontology.OTHER_IDENTIFIERS) != null) {
            logger.debug("otherIdentifiers is not null");
            unit.put(Ontology.OTHER_IDENTIFIERS, itemData.get(Ontology.OTHER_IDENTIFIERS));
        }
        return unit;
    }

    @Override
    public Accessible importItem(Map<String, Object> itemData) throws ValidationError {
        return importItem(itemData, new Stack<>());
    }
}
