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

/**
 */
package eu.ehri.project.importers.ead;

import eu.ehri.project.importers.AbstractImporter;
import eu.ehri.project.importers.properties.XmlImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Handler for importing EAD files converted from the USHMM Solr index file.
 * These files were converted using the solr2ead XSLT stylesheet.
 */
public class UshmmHandler extends EadHandler {

    private static final Logger logger = LoggerFactory.getLogger(UshmmHandler.class);

    private int count;

    /**
     * @param importer
     */
    public UshmmHandler(AbstractImporter<Map<String, Object>> importer) {
        super(importer, new XmlImportProperties("ushmm.properties"));

    }

    /**
     * Generic constructor, available so that the generic REST call can be made on the USHMM Handler.
     *
     * @param importer
     * @param props
     */
    public UshmmHandler(AbstractImporter<Map<String, Object>> importer, XmlImportProperties props) {
        super(importer, props);

    }

    @Override
    protected void extractIdentifier(Map<String, Object> currentGraph) {
        //not all units have ids, and some have multiple, find the "irn"
        if (currentGraph.containsKey("objectIdentifier")) {
            if (currentGraph.get("objectIdentifier") instanceof List) {
                logger.debug("class of identifier: " + currentGraph.get("objectIdentifier").getClass());
                List<String> identifiers = (List<String>) currentGraph.get("objectIdentifier");
                List<String> identifierType = (List<String>) currentGraph.get("objectIdentifierType");
                for (int i = 0; i < identifiers.size(); i++) {
                    if (identifierType.get(i).equals("irn")) {
                        logger.debug("found official id: " + identifiers.get(i));
                        currentGraph.put("objectIdentifier", identifiers.get(i));
                    } else {
                        logger.debug("found other form of identifier: " + identifiers.get(i));
                        addOtherIdentifier(currentGraph, identifiers.get(i));
                        //currentGraph.put("otherIdentifiers", identifiers.get(i));
                    }
                }
                currentGraph.remove("objectIdentifierType");
            }
        } else {
            logger.error("no unitid found, setting {}", ++count);
            currentGraph.put("objectIdentifier", "ushmmID" + count);
        }
    }
}