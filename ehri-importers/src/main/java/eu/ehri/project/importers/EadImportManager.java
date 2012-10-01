package eu.ehri.project.importers;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;

import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.exceptions.InputParseError;
import eu.ehri.project.importers.exceptions.InvalidEadDocument;
import eu.ehri.project.importers.exceptions.InvalidInputFormatError;
import eu.ehri.project.models.Action;
import eu.ehri.project.models.Agent;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.persistance.ActionManager;

/**
 * Class that provides a front-end for importing EAD XML files, EADGRP, and
 * nested lists of EAD documents into the graph.
 * 
 * @author michaelb
 * 
 */
public class EadImportManager extends XmlImportManager implements ImportManager {

    private static final String EADGRP_PATH = "//eadgrp/archdescgrp/dscgrp/ead";

    private static final String EADLIST_PATH = "//eadlist/ead";

    private static final Logger logger = LoggerFactory
            .getLogger(EadImportManager.class);

    private Boolean tolerant = false;

    protected final FramedGraph<Neo4jGraph> framedGraph;
    protected final Agent agent;
    protected final Actioner actioner;
    
    // Ugly stateful variables for tracking import state
    // and reporting errors usefully...
    private String currentFile = null;
    private Integer currentPosition = null;

    /**
     * Constructor.
     * 
     * @param framedGraph
     * @param agent
     * @param actioner
     */
    public EadImportManager(FramedGraph<Neo4jGraph> framedGraph,
            final Agent agent, final Actioner actioner) {
        this.framedGraph = framedGraph;
        this.agent = agent;
        this.actioner = actioner;
    }

    /**
     * Tell the importer to simply skip invalid items rather than throwing an
     * exception.
     * 
     * @param tolerant
     */
    public void setTolerant(Boolean tolerant) {
        logger.info("Setting importer to tolerant: " + tolerant);
        this.tolerant = tolerant;
    }

    /**
     * Import a file, creating a new action with the given log message.
     * 
     * @param ios
     * @param logMessage
     * @return
     * 
     * @throws IOException
     * @throws ValidationError
     * @throw InputParseError
     */
    public ImportLog importFile(InputStream ios, String logMessage)
            throws IOException, ValidationError, InputParseError {
        Transaction tx = framedGraph.getBaseGraph().getRawGraph().beginTx();
        try {
            // Create a new action for this import
            final Action action = new ActionManager(framedGraph).createAction(
                    actioner, logMessage);
            // Create a manifest to store the results of the import.
            final ImportLog log = new ImportLog(action);

            // Do the import...
            importFile(ios, action, log);

            // If nothing was imported, remove the action...
            if (log.isValid())
                tx.success();
            else
                tx.failure();
            return log;
        } catch (ValidationError e) {
            tx.failure();
            throw e;
        } catch (Exception e) {
            tx.failure();
            throw new RuntimeException(e);
        } finally {
            tx.finish();
        }

    }

    /**
     * Import multiple files in the same batch/transaction.
     * 
     * @param paths
     * @param logMessage
     * 
     * @throws IOException
     * @throws ValidationError
     */
    public ImportLog importFiles(List<String> paths, String logMessage)
            throws IOException, ValidationError {

        Transaction tx = framedGraph.getBaseGraph().getRawGraph().beginTx();
        try {

            final Action action = new ActionManager(framedGraph).createAction(
                    actioner, logMessage);
            final ImportLog log = new ImportLog(action);
            for (String path : paths) {
                currentFile = path;
                FileInputStream ios = new FileInputStream(path);
                try {
                    logger.info("Importing file: " + path);
                    importFile(ios, action, log);
                } finally {
                    ios.close();
                }
            }

            // If nothing was imported, remove the action...
            if (log.isValid())
                tx.success();
            else
                tx.failure();
            return log;
        } catch (ValidationError e) {
            tx.failure();
            throw e;
        } catch (Exception e) {
            tx.failure();
            throw new RuntimeException(e);
        } finally {
            tx.finish();
        }
    }

    /**
     * Import EAD from the given InputStream, as part of the given action.
     * 
     * @param ios
     * @param action
     * @param log
     * 
     * @throws IOException
     * @throws ValidationError
     * @throws InputParseError
     * @throws InvalidInputFormatError
     * @throws InvalidEadDocument
     */
    private void importFile(InputStream ios, final Action action,
            final ImportLog log) throws IOException, ValidationError,
            InputParseError, InvalidEadDocument, InvalidInputFormatError {

        // XML parsing boilerplate...
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(ios);
            importDocWithAction(doc, action, log);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new InputParseError(e);
        }

    }

    /**
     * Import an XML doc using the given action.
     * 
     * @param doc
     * @param action
     * 
     * @return
     * @throws ValidationError
     * @throws InvalidEadDocument
     * @throws InvalidInputFormatError
     */
    private void importDocWithAction(Document doc, final Action action,
            final ImportLog manifest) throws ValidationError,
            InvalidEadDocument, InvalidInputFormatError {

        // Check the various types of document we support. This
        // includes <eadgrp> or <eadlist> types.
        if (doc.getDocumentElement().getNodeName().equals("ead")) {
            importNodeWithAction((Node) doc.getDocumentElement(), action,
                    manifest);
        } else if (doc.getDocumentElement().getNodeName().equals("eadlist")) {
            importNestedItems(doc, EADLIST_PATH, action, manifest);
        } else if (doc.getDocumentElement().getNodeName().equals("eadgrp")) {
            importNestedItems(doc, EADGRP_PATH, action, manifest);
        } else {
            throw new InvalidEadDocument(doc.getDocumentElement().getNodeName());
        }
    }

    /**
     * @param doc
     * @param path
     * @param action
     * @return
     * @throws ValidationError
     * @throws InvalidInputFormatError
     */
    private void importNestedItems(Document doc, String path,
            final Action action, final ImportLog manifest)
            throws ValidationError, InvalidInputFormatError {
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList eadList;
        try {
            eadList = (NodeList) xpath.compile(path).evaluate(doc,
                    XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        for (int i = 0; i < eadList.getLength(); i++) {
            currentPosition = i;
            importNodeWithAction(eadList.item(i), action, manifest);
        }
    }

    /**
     * Import a Node doc using the given action.
     * 
     * @param node
     * @param action
     * 
     * @return
     * @throws ValidationError
     * @throws InvalidInputFormatError
     */
    private void importNodeWithAction(Node node, final Action action,
            final ImportLog log) throws ValidationError,
            InvalidInputFormatError {

        EadImporter importer = new EadImporter(framedGraph, agent, node, log);
        // Create a new action for this import
        importer.addCreationCallback(new CreationCallback() {
            public void itemImported(AccessibleEntity item) {
                action.addSubjects(item);
                log.addCreated();
            }
        });
        importer.addUpdateCallback(new CreationCallback() {
            public void itemImported(AccessibleEntity item) {
                action.addSubjects(item);
                log.addUpdated();
            }
        });
        try {
            importer.importItems();
        } catch (InvalidInputFormatError e) {
            logger.error(e.getMessage());
            log.setErrored(formatErrorLocation(), e.getMessage());
            if (!tolerant)
                throw e;
        } catch (ValidationError e) {
            logger.error(e.getMessage());
            log.setErrored(formatErrorLocation(), e.getMessage());
            if (!tolerant)
                throw e;
        }
    }
    
    private String formatErrorLocation() {
        return String.format("File: %s, EAD document: %d", currentFile, currentPosition);
    }
}