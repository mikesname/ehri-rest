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

package eu.ehri.project.importers.eag;

import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.EaImporter;
import eu.ehri.project.importers.ImportLog;
import eu.ehri.project.importers.eac.EacImporter;
import eu.ehri.project.models.Country;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.persistence.BundleDAO;
import eu.ehri.project.persistence.Mutation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importer of EAG-based descriptions.
 */
public class EagImporter extends EaImporter {

    private static final Logger logger = LoggerFactory.getLogger(EacImporter.class);
    private final Pattern priorityPattern = Pattern.compile("Priority: (-?\\d+)");
    public static final String MAINTENANCE_NOTES = "maintenanceNotes";
    public static final String PRIORITY = "priority";

    /**
     * Construct an EagImporter object.
     *
     * @param framedGraph     The graph instance
     * @param permissionScope A permission scope, e.g. a country
     * @param log             An import log instance
     */
    public EagImporter(FramedGraph<?> framedGraph, PermissionScope permissionScope, ImportLog log) {
        super(framedGraph, permissionScope, log);
    }

    @Override
    public Repository importItem(Map<String, Object> itemData, List<String> idPath) throws
            ValidationError {
        return importItem(itemData);
    }

    @Override
    public Map<String, Object> extractUnit(Map<String, Object> itemData) throws ValidationError {
        Map<String, Object> data = super.extractUnit(itemData);
        // MB: Hack hack hack - extract EHRI-specific 'priority' field out of the
        // pattern "Priority: <digit>" in the maintenanceNotes field.
        Object notes = itemData.get(MAINTENANCE_NOTES);
        if (notes != null) {
            if (notes instanceof ArrayList<?>) {
                for (Object n : (ArrayList<?>) notes) {
                    if (n instanceof String) {
                        Matcher m = priorityPattern.matcher((String) n);
                        if (m.find()) {
                            data.put(PRIORITY, Integer.parseInt(m.group(1)));
                        }
                    }
                }
            }
        }

        return data;
    }

    /**
     * @param itemData A data tree
     * @throws ValidationError
     */
    @Override
    public Repository importItem(Map<String, Object> itemData) throws ValidationError {

        BundleDAO persister = new BundleDAO(framedGraph, permissionScope.idPath());

        Map<String, Object> descmap = extractUnitDescription(itemData, EntityClass.REPOSITORY_DESCRIPTION);
        descmap.put(Ontology.IDENTIFIER_KEY, descmap.get(Ontology.IDENTIFIER_KEY) + "#desc");
        Bundle descBundle = new Bundle(EntityClass.REPOSITORY_DESCRIPTION, descmap);

        // Add dates and descriptions to the bundle since they're @Dependent
        // relations.
        for (Map<String, Object> dpb : extractDates(itemData)) {
            descBundle = descBundle.withRelation(Ontology.ENTITY_HAS_DATE, new Bundle(EntityClass.DATE_PERIOD, dpb));
        }

        //add the address to the description bundle
        Map<String, Object> address = extractAddress(itemData);
        if (!address.isEmpty()) {
            descBundle = descBundle.withRelation(Ontology.ENTITY_HAS_ADDRESS, new Bundle(EntityClass.ADDRESS, address));
        }
        Map<String, Object> unknowns = extractUnknownProperties(itemData);
        if (!unknowns.isEmpty()) {
            logger.debug("Unknown Properties found");
            descBundle = descBundle.withRelation(Ontology.HAS_UNKNOWN_PROPERTY, new Bundle(EntityClass.UNKNOWN_PROPERTY, unknowns));
        }
        for (Map<String, Object> dpb : extractMaintenanceEvent(itemData)) {
            logger.debug("maintenance event found");
            //dates in maintenanceEvents are no DatePeriods, they are not something to search on
            descBundle = descBundle.withRelation(Ontology.HAS_MAINTENANCE_EVENT, new Bundle(EntityClass.MAINTENANCE_EVENT, dpb));
        }

        Bundle unit = new Bundle(EntityClass.REPOSITORY, extractUnit(itemData))
                .withRelation(Ontology.DESCRIPTION_FOR_ENTITY, descBundle);

        Mutation<Repository> mutation = persister.createOrUpdate(unit, Repository.class);
        handleCallbacks(mutation);

        if (mutation.created()) {
            mutation.getNode().setCountry(framedGraph.frame(permissionScope.asVertex(), Country.class));
            mutation.getNode().setPermissionScope(permissionScope);
        }
        return mutation.getNode();
    }
}