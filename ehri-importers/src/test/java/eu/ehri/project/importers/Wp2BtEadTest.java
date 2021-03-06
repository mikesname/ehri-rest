package eu.ehri.project.importers;

import com.tinkerpop.blueprints.Vertex;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.importers.properties.XmlImportProperties;
import eu.ehri.project.models.DocumentDescription;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.models.events.SystemEvent;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.views.impl.CrudViews;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;
import org.junit.Ignore;

/**
 * @author Linda Reijnhoudt (https://github.com/lindareijnhoudt)
 */
public class Wp2BtEadTest extends AbstractImporterTest {

    private static final Logger logger = LoggerFactory.getLogger(Wp2BtEadTest.class);
    protected final String SINGLE_EAD = "wp2_bt_ead_small.xml";
    // Depends on fixtures
    protected final String TEST_REPO = "r1";
    // Depends on hierarchical-ead.xml
    protected final String C1_A = "000.001.0";
    protected final String C1_B = "000.002.0";
    protected final String C1_A_C2 = "000.001.1";
    protected final String C1_B_C2_A = "000.002.1";
    protected final String C1_B_C2_B = "000.002.2";
    protected final String FONDS = "wp2-bt";

    @Test
    public void testImportItemsT() throws Exception {

        Repository agent = manager.getFrame(TEST_REPO, Repository.class);
        Bundle vocabularyBundle = new Bundle(EntityClass.CVOC_VOCABULARY)
                                .withDataValue(Ontology.IDENTIFIER_KEY, "WP2_keywords")
                                .withDataValue(Ontology.NAME_KEY, "WP2 Keywords");
        Bundle conceptBundle = new Bundle(EntityClass.CVOC_CONCEPT)
                                .withDataValue(Ontology.IDENTIFIER_KEY, "KEYWORD.JMP.716");
        Vocabulary vocabulary = new CrudViews<Vocabulary>(graph, Vocabulary.class).create(vocabularyBundle, validUser);
        logger.debug(vocabulary.getId());
        Concept concept_716 = new CrudViews<Concept>(graph, Concept.class).create(conceptBundle, validUser); 
        vocabulary.addItem(concept_716);
        
        
        Vocabulary vocabularyTest = manager.getFrame("wp2_keywords", Vocabulary.class);
        assertNotNull(vocabularyTest);
        
        final String logMessage = "Importing Beit Terezin EAD";
 // Before...
       List<VertexProxy> graphState1 = getGraphState(graph);

        int count = getNodeCount(graph);
        InputStream iosVC = ClassLoader.getSystemResourceAsStream(SINGLE_EAD);
//        SaxImportManager importManagerVC = new SaxImportManager(graph, agent, validUser, EadIntoVirtualCollectionImporter.class, EadIntoVirtualCollectionHandler.class);
        SaxImportManager importManager = new SaxImportManager(graph, agent, validUser, EadImporter.class, EadHandler.class, new XmlImportProperties("wp2ead.properties"));

        
        importManager.setTolerant(Boolean.TRUE);
        
        ImportLog logVC = importManager.importFile(iosVC, logMessage);
         // After...
       List<VertexProxy> graphState2 = getGraphState(graph);
       GraphDiff diff = diffGraph(graphState1, graphState2);
       diff.printDebug(System.out);

//        printGraph(graph);
        // How many new nodes will have been created? We should have
        // - 6 more DocumentaryUnits fonds 2C1 3C2
        // - 6 more DocumentDescription
        // - 1 more DatePeriod 0 0 1 
        // - 17 UndeterminedRelationship, 0 2 2 4 4 5
        // - 7 more import Event links (6 for every Unit, 1 for the User)
        // - 1 more import Event
        // - 1 Annotation as resolved relationship 
        int newCount = count + 39;
        assertEquals(newCount, getNodeCount(graph));

        Iterable<Vertex> docs = graph.getVertices(Ontology.IDENTIFIER_KEY, FONDS);
//        assertTrue(docs.iterator().hasNext());
                
        DocumentaryUnit fonds = graph.frame(getVertexByIdentifier(graph, FONDS), DocumentaryUnit.class);
        Iterator<DocumentDescription> i = fonds.getDocumentDescriptions().iterator();
        int countDocDesc = 0;
        while(i.hasNext()){
            DocumentDescription desc  = i.next();
            assertEquals("mul", desc.getLanguageOfDescription());
            countDocDesc++;
        }        
        assertEquals(1, countDocDesc);
        
        

        // check the child items
        DocumentaryUnit c1_a = graph.frame(getVertexByIdentifier(graph, C1_A), DocumentaryUnit.class);
        DocumentaryUnit c1_b = graph.frame(getVertexByIdentifier(graph, C1_B), DocumentaryUnit.class);
        DocumentaryUnit c1_a_c2 = graph.frame(getVertexByIdentifier(graph, C1_A_C2), DocumentaryUnit.class);
        DocumentaryUnit c1_b_c2_a = graph.frame(getVertexByIdentifier(graph, C1_B_C2_A), DocumentaryUnit.class);
        DocumentaryUnit c1_b_c2_b = graph.frame(getVertexByIdentifier(graph, C1_B_C2_B), DocumentaryUnit.class);

        assertEquals(fonds, c1_a.getParent());
        assertEquals(fonds, c1_b.getParent());
        
        assertEquals(c1_a, c1_a_c2.getParent());

        assertEquals(c1_b, c1_b_c2_a.getParent());
        assertEquals(c1_b, c1_b_c2_b.getParent());

        // Ensure the import action has the right number of subjects.
        //        Iterable<Action> actions = unit.getHistory();
        // Check we've created 6 items
        assertEquals(6, logVC.getCreated());
        assertTrue(logVC.getAction() != null);
        assertEquals(logMessage, logVC.getAction().getLogMessage());

        //assert keywords are matched to cvocs
        assertTrue(toList(c1_b.getLinks()).size() > 0);
        for(Link a : c1_b.getLinks()){
            logger.debug(a.getLinkType());
        }

        List<AccessibleEntity> subjects = toList(logVC.getAction().getSubjects());
        for (AccessibleEntity subject : subjects) {
            logger.info("identifier: " + subject.getId());
        }

        assertEquals(6, subjects.size());
        assertEquals(logVC.getChanged(), subjects.size());

        // Check permission scopes
        assertEquals(agent, fonds.getPermissionScope());
        assertEquals(fonds, c1_a.getPermissionScope());
        assertEquals(fonds, c1_b.getPermissionScope());
        assertEquals(c1_a, c1_a_c2.getPermissionScope());
        assertEquals(c1_b, c1_b_c2_a.getPermissionScope());
        assertEquals(c1_b, c1_b_c2_b.getPermissionScope());
        
        // Check the author of the description
        for (DocumentDescription d : fonds.getDocumentDescriptions()){
            assertEquals("EHRI", d.asVertex().getProperty("processInfo"));
        }

        // Check the importer is Idempotent
        ImportLog log2 = importManager.importFile(ClassLoader.getSystemResourceAsStream(SINGLE_EAD), logMessage);
        assertEquals(6, log2.getUnchanged());
        //assertEquals(0, log2.getChanged());
        assertEquals(newCount, getNodeCount(graph));
    }
}
