package eu.ehri.project.importers;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.exceptions.InputParseError;
import eu.ehri.project.importers.exceptions.InvalidInputFormatError;
import eu.ehri.project.importers.exceptions.InvalidXmlDocument;
import eu.ehri.project.importers.properties.XmlImportProperties;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.persistence.ActionManager;
import eu.ehri.project.persistence.Mutation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/**
 * Class that provides a front-end for importing XML files like EAD and EAC and
 * nested lists of EAD documents into the graph.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class SaxImportManager extends AbstractImportManager {

    private static final Logger logger = LoggerFactory.getLogger(SaxImportManager.class);

    private final Class<? extends SaxXmlHandler> handlerClass;
    private final Optional<XmlImportProperties> properties;
    private final List<ImportCallback> extraCallbacks;

    /**
     * Constructor.
     *
     * @param graph     the framed graph
     * @param scope     the permission scope
     * @param actioner  the actioner
     */
    public SaxImportManager(FramedGraph<? extends TransactionalGraph> graph,
            final PermissionScope scope, final Actioner actioner,
            Class<? extends AbstractImporter> importerClass, Class<? extends SaxXmlHandler> handlerClass,
            Optional<XmlImportProperties> properties,
            List<ImportCallback> callbacks) {
        super(graph, scope, actioner, importerClass);
        this.handlerClass = handlerClass;
        this.properties = properties;
        this.extraCallbacks = Lists.newArrayList(callbacks);
        logger.info("importer used: " + importerClass);
        logger.info("handler used: " + handlerClass);
    }

    /**
     * Constructor.
     *
     * @param graph     the framed graph
     * @param scope     a permission scope
     * @param actioner  the actioner
     */
    public SaxImportManager(FramedGraph<? extends TransactionalGraph> graph,
            final PermissionScope scope, final Actioner actioner,
            Class<? extends AbstractImporter> importerClass, Class<? extends SaxXmlHandler> handlerClass,
            List<ImportCallback> callbacks) {
        this(graph, scope, actioner, importerClass, handlerClass, Optional.<XmlImportProperties>absent(), callbacks);
    }

    /**
     * Constructor.
     *
     * @param graph     the framed graph
     * @param scope     a permission scope
     * @param actioner  the actioner
     */
    public SaxImportManager(FramedGraph<? extends TransactionalGraph> graph,
            final PermissionScope scope, final Actioner actioner,
            Class<? extends AbstractImporter> importerClass, Class<? extends SaxXmlHandler> handlerClass,
            XmlImportProperties properties) {
        this(graph, scope, actioner, importerClass, handlerClass, Optional.fromNullable(properties),
                Lists.<ImportCallback>newArrayList());
    }

    /**
     * Constructor.
     *
     * @param graph     the framed graph
     * @param scope     a permission scope
     * @param actioner  the actioner
     */
    public SaxImportManager(FramedGraph<? extends TransactionalGraph> graph,
            final PermissionScope scope, final Actioner actioner,
            Class<? extends AbstractImporter> importerClass, Class<? extends SaxXmlHandler> handlerClass) {
        this(graph, scope, actioner, importerClass, handlerClass, Lists.<ImportCallback>newArrayList());
    }

    /**
     * Import XML from the given InputStream, as part of the given action.
     *
     * @param ios           an input stream
     * @param eventContext  the event context
     * @param log           a logger object
     * @throws IOException
     * @throws ValidationError
     * @throws InputParseError
     * @throws InvalidInputFormatError
     * @throws InvalidXmlDocument
     */
    @Override
    protected void importFile(InputStream ios, final ActionManager.EventContext eventContext,
            final ImportLog log) throws IOException, ValidationError,
            InputParseError, InvalidXmlDocument, InvalidInputFormatError {

        try {
            AbstractImporter<Map<String, Object>> importer = importerClass.getConstructor(FramedGraph.class, PermissionScope.class,
                    ImportLog.class).newInstance(framedGraph, permissionScope, log);
            
            for (ImportCallback callback : extraCallbacks) {
                importer.addCallback(callback);
            }

            // Add housekeeping callbacks for the log object...
            importer.addCallback(new ImportCallback() {
                public void itemImported(Mutation<? extends AccessibleEntity> mutation) {
                    switch (mutation.getState()) {
                        case CREATED:
                            logger.info("Item created: {}", mutation.getNode().getId());
                            eventContext.addSubjects(mutation.getNode());
                            log.addCreated();
                            break;
                        case UPDATED:
                            logger.info("Item updated: {}", mutation.getNode().getId());
                            eventContext.addSubjects(mutation.getNode());
                            log.addUpdated();
                            break;
                        default:
                            log.addUnchanged();
                    }
                }
            });
            //TODO decide which handler to use, HandlerFactory? now part of constructor ...
            SaxXmlHandler handler = properties.isPresent()
                    ? handlerClass.getConstructor(AbstractImporter.class, XmlImportProperties.class)
                            .newInstance(importer, properties.get())
                    : handlerClass.getConstructor(AbstractImporter.class).newInstance(importer);

            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(false);
            if (isTolerant()) {
                logger.debug("Turning off validation and setting schema to null");
                spf.setValidating(false);
                spf.setSchema(null);
            }
            logger.debug("isValidating: " + spf.isValidating());
            SAXParser saxParser = spf.newSAXParser();
            saxParser.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
            saxParser.parse(ios, handler);
        } catch (InstantiationException ex) {
            logger.error("InstantiationException: " + ex.getMessage());
        } catch (IllegalAccessException ex) {
            logger.error("IllegalAccess: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.error("IllegalArgumentException: " + ex.getMessage());
            ex.printStackTrace(System.out);
        } catch (InvocationTargetException ex) {
            logger.error("InvocationTargetException: " + ex.getMessage());
        } catch (NoSuchMethodException ex) {
            logger.error("NoSuchMethodException: " + ex.getMessage());
        } catch (SecurityException ex) {
            logger.error("SecurityException: " + ex.getMessage());
        } catch (ParserConfigurationException ex) {
            logger.error("ParserConfigurationException: " + ex.getMessage());
            throw new RuntimeException(ex);
        } catch (SAXException e) {
            logger.error("SAXException: " + e.getMessage());
            throw new InputParseError(e);
        }
    }

    public SaxImportManager withProperties(XmlImportProperties properties) {
        return new SaxImportManager(framedGraph, permissionScope, actioner, importerClass, handlerClass,
                Optional.<XmlImportProperties>of(properties), extraCallbacks);
    }

    public SaxImportManager withProperties(String properties) {
        if (properties == null) {
            return new SaxImportManager(framedGraph, permissionScope, actioner, importerClass, handlerClass);
        } else {
            XmlImportProperties xmlImportProperties = new XmlImportProperties(properties);
            return new SaxImportManager(framedGraph, permissionScope, actioner, importerClass, handlerClass,
                    Optional.<XmlImportProperties>of(xmlImportProperties), extraCallbacks);
        }
    }
}
