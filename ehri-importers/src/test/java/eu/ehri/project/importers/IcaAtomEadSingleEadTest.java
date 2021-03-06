package eu.ehri.project.importers;

import com.tinkerpop.blueprints.Vertex;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.events.SystemEvent;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;


import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 * @author Linda Reijnhoudt (https://github.com/lindareijnhoudt)
 */
public class IcaAtomEadSingleEadTest extends AbstractImporterTest {
    protected final String SINGLE_EAD = "single-ead.xml";

    // Depends on fixtures
    protected final String TEST_REPO = "r1";

    // Depends on single-ead.xml
    protected final String IMPORTED_ITEM_ID = "C00001";

    @Test
    public void testImportItemsT() throws Exception {
        Repository agent = manager.getFrame(TEST_REPO, Repository.class);
        final String logMessage = "Importing a single EAD by IcaAtomEadSingleEad";

        int origCount = getNodeCount(graph);

        InputStream ios = ClassLoader.getSystemResourceAsStream(SINGLE_EAD);
        ImportLog log = new SaxImportManager(graph, agent, validUser, IcaAtomEadImporter.class, IcaAtomEadHandler.class).setTolerant(Boolean.TRUE).importFile(ios, logMessage);

        printGraph(graph);
        // How many new nodes will have been created? We should have
        // - 1 more DocumentaryUnit
        // - 1 more DocumentDescription
        // - 2 more DatePeriod
        //TODO: test these UR's
        // - 5 more UndeterminedRelationships
        //TODO: test this UP
        // - 1 more UnknownProperty
        // - 2 more import Event links
        // - 1 more import Event

        Iterable<Vertex> docs = graph.getVertices("identifier", IMPORTED_ITEM_ID);
        assertTrue(docs.iterator().hasNext());
        DocumentaryUnit unit = graph.frame(docs.iterator().next(), DocumentaryUnit.class);
        for(Description d : unit.getDocumentDescriptions())
            assertEquals("Test EAD Item", d.getName());

        // Test scope and content has correctly decoded data.
        // This tests two bugs:
        //  Space stripping: https://github.com/mikesname/ehri-rest/issues/12
        //  Paragraph ordering: https://github.com/mikesname/ehri-rest/issues/13
        Description firstDesc = unit.getDocumentDescriptions().iterator().next();
        String scopeContent = firstDesc.asVertex().getProperty("scopeAndContent");
        String expected =
                "This is some test scope and content.\n\n" +
                "This contains Something & Something else.\n\n" +
                "This is another paragraph.";

        assertEquals(expected, scopeContent);

        // Check the right nodes get created.
        int createCount = origCount + 13;

        // - 4 more UnderterminedRelationship nodes

        assertEquals(createCount, getNodeCount(graph));

        // Yet we've only created 1 *logical* item...
        assertEquals(1, log.getChanged());

        List<SystemEvent> actions = toList(unit.getHistory());
        // Check we've only got one action
        assertEquals(1, actions.size());
        assertEquals(logMessage, actions.get(0).getLogMessage());

        // Now re-import the same file
        InputStream ios2 = ClassLoader.getSystemResourceAsStream(SINGLE_EAD);
        ImportLog log2 = new SaxImportManager(graph, agent, validUser, IcaAtomEadImporter.class, IcaAtomEadHandler.class).importFile(ios2, logMessage);

        // We should no new nodes (not even a SystemEvent)
        assertEquals(createCount, getNodeCount(graph));
        // And no logical item should've been updated
        assertEquals(0, log2.getUpdated());

        // Check permission scopes
        for (AccessibleEntity e : log.getAction().getSubjects()) {
            assertEquals(agent, e.getPermissionScope());
        }
    }
}
