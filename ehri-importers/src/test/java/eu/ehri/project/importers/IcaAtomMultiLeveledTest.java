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

package eu.ehri.project.importers;

import com.google.common.collect.Lists;
import com.tinkerpop.blueprints.Vertex;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.events.SystemEvent;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Linda Reijnhoudt (https://github.com/lindareijnhoudt)
 */
public class IcaAtomMultiLeveledTest extends AbstractImporterTest{
    private static final Logger logger = LoggerFactory.getLogger(IcaAtomMultiLeveledTest.class);
     protected final String SINGLE_EAD = "zbirka-gradiva-za-povijest-zidova-collection-of-material-concerning-history-of-jews.xml";
private final String UN_REL = "HR-HDA145corporateBodyAccessCroatianStateArchive";
    // Depends on fixtures
    protected final String TEST_REPO = "r1";

    // Depends on single-ead.xml
    protected final String IMPORTED_ITEM_ID = "HR r000382HR HR-HDA 1551";

    @Test
    public void testImportItemsT() throws Exception {
        Repository agent = manager.getFrame(TEST_REPO, Repository.class);
        final String logMessage = "Importing a multileveled EAD by IcaAtomMultiLeveled";

        int origCount = getNodeCount(graph);

        InputStream ios = ClassLoader.getSystemResourceAsStream(SINGLE_EAD);
        ImportLog log = new SaxImportManager(graph, agent, validUser, IcaAtomEadImporter.class, IcaAtomEadHandler.class).setTolerant(Boolean.TRUE).importFile(ios, logMessage);

        printGraph(graph);
        // How many new nodes will have been created? We should have
        // - 3 more DocumentaryUnit (1 1 1)
        // - 3 more DocumentDescription (1 1 1)
        // - 3 more DatePeriod (1 1 1)
        //TODO: test these UR's
        // - 8 more UndeterminedRelationships (2 4 2)
        //TODO: test this UP
        // - 1 more UnknownProperty (1 1 1)
        // - 4 more import Event links (+1)
        // - 1 more import Event

        int createCount = origCount + 23;


        assertEquals(createCount, getNodeCount(graph));

        // Yet we've only created 3 *logical* items...
        assertEquals(0, log.getUpdated());
        assertEquals(3, log.getCreated());

        Iterable<Vertex> docs = graph.getVertices("identifier",
                IMPORTED_ITEM_ID);
        assertTrue(docs.iterator().hasNext());
        DocumentaryUnit unit = graph.frame(docs.iterator().next(), DocumentaryUnit.class);

        // Check unit ID
        String targetUnitId = "nl-r1-hr_r000382hr_hr_hda_1551";
        assertEquals(targetUnitId, unit.getId());

        for(Description d : unit.getDocumentDescriptions())
            assertEquals("Zbirka gradiva za povijest Židova (Collection of material concerning the history of Jews)", d.getName());

        assertEquals(2L, unit.getChildCount());
        List<DocumentaryUnit> children = Lists.newArrayList(unit.getChildren());
        DocumentaryUnit child1 = children.get(0);
        DocumentaryUnit child2 = children.get(1);

        // Check permission scopes
        assertEquals(agent, unit.getPermissionScope());
        assertEquals(unit, child1.getPermissionScope());
        assertEquals(unit, child2.getPermissionScope());

        // Check child IDs
        assertEquals(targetUnitId + "-hr_hda_145", child1.getId());
        assertEquals(targetUnitId + "-hr_hda_223", child2.getId());

        List<SystemEvent> actions = toList(unit.getHistory());
        // Check we've only got one action
        assertEquals(1, actions.size());
        assertEquals(logMessage, actions.get(0).getLogMessage());

        // Now re-import the same file
        InputStream ios2 = ClassLoader.getSystemResourceAsStream(SINGLE_EAD);
        ImportLog log2 = new SaxImportManager(graph, agent, validUser, IcaAtomEadImporter.class, IcaAtomEadHandler.class).importFile(ios2, logMessage);

        // We should have no new nodes, not even SystemEvent
        assertEquals(createCount, getNodeCount(graph));
        // And no logical item should've been updated
        assertEquals(0, log2.getUpdated());
    }
}
