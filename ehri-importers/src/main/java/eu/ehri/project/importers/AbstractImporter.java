package eu.ehri.project.importers;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.properties.NodeProperties;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.MaintenanceEvent;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.persistence.BundleDAO;
import eu.ehri.project.persistence.Mutation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Base class for importers that import documentary units, historical agents and virtual collections,
 * with their constituent logical data, description(s), and date periods.
 *
 * @param <T> Type of node representation that can be imported. In this version,
 *            the only implementation is for <code>Map<String, Object></code>
 * @author Mike Bryant (http://github.com/mikesname)
 */
public abstract class AbstractImporter<T> {

    private static final String NODE_PROPERTIES = "allowedNodeProperties.csv";

    private static final Logger logger = LoggerFactory.getLogger(AbstractImporter.class);
    private static final Joiner stringJoiner = Joiner.on("\n\n").skipNulls();

    protected final PermissionScope permissionScope;
    protected final FramedGraph<?> framedGraph;
    protected final GraphManager manager;
    protected final ImportLog log;
    protected List<ImportCallback> callbacks = new LinkedList<ImportCallback>();

    private NodeProperties pc;

    /**
     * Call all registered ImportCallbacks for the given mutation.
     * @param mutation the Mutation to handle callbacks for
     */
    protected void handleCallbacks(Mutation<? extends AccessibleEntity> mutation) {
        for (ImportCallback callback : callbacks) {
            callback.itemImported(mutation);
        }
    }

    public PermissionScope getPermissionScope() {
        return permissionScope;
    }

    public BundleDAO getPersister(List<String> scopeIds) {
        return new BundleDAO(framedGraph,
                Iterables.concat(permissionScope.idPath(), scopeIds));
    }

    public BundleDAO getPersister() {
        return new BundleDAO(framedGraph, permissionScope.idPath());
    }

    /**
     * Constructor.
     *
     * @param graph     the framed graph
     * @param scope     the permission scope
     * @param log       the log object
     */
    public AbstractImporter(FramedGraph<?> graph, PermissionScope scope, ImportLog log) {
        this.permissionScope = scope;
        this.framedGraph = graph;
        this.log = log;
        manager = GraphManagerFactory.getInstance(graph);
    }

    /**
     * Add a callback to run when an item is created.
     *
     * @param callback a callback function object
     */
    public void addCallback(final ImportCallback callback) {
        callbacks.add(callback);
    }

    /**
     * Import an item representation into the graph, and return the Node.
     * 
     * @param itemData the item representation to import
     * @return the imported node
     * @throws ValidationError when the item representation does not validate
     */
    public abstract AccessibleEntity importItem(T itemData) throws ValidationError;

    /**
     * Import an item representation into the graph at a certain depth, and return the Node.
     * 
     * @param itemData the item representation to import
     * @param scopeIds parent identifiers for ID generation,
     *                 not including permission scope
     * @return the imported node
     * @throws ValidationError when the item representation does not validate
     */
    public abstract AccessibleEntity importItem(T itemData,
            List<String> scopeIds) throws ValidationError;

    /**
     * Extract a list of DatePeriod bundles from an item's data.
     *
     * @param data  the raw map of date data
     * @return returns a List of Maps with DatePeriod.START_DATE and DatePeriod.END_DATE values
     */
    public abstract Iterable<Map<String, Object>> extractDates(T data);


    /**
     * only properties that have the multivalued-status can actually be multivalued. all other properties will be
     * flattened by this method.
     *
     * @param key       a property key
     * @param value     a property value
     * @param entity    the EntityClass with which this frameMap must comply
     */
    protected Object changeForbiddenMultivaluedProperties(String key, Object value, EntityClass entity) {
        if (pc == null) {
            pc = new NodeProperties();
            try {
                InputStream fis = getClass().getClassLoader().getResourceAsStream(NODE_PROPERTIES);
                if (fis == null) {
                    throw new RuntimeException("Missing properties file: " + NODE_PROPERTIES);
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
                String firstline = br.readLine();
                pc.setTitles(firstline);

                String line;
                while ((line = br.readLine()) != null) {
                    pc.addRow(line);
                }
            } catch (IOException ex) {
                logger.error(ex.getMessage());
            }
        }
        if (value instanceof List
                && (!pc.hasProperty(entity.getName(), key) || !pc.isMultivaluedProperty(entity.getName(), key))) {
            return stringJoiner.join((List<String>) value);
        } else {
            return value;
        }
    }
    
    public abstract Iterable<Map<String, Object>> extractMaintenanceEvent(T itemData);
    public abstract Map<String, Object> getMaintenanceEvent(T event);
    public abstract MaintenanceEvent importMaintenanceEvent(T event);

/**
 * all data that is stored above the first imported DocumentaryUnit will be processed here, and added to the DocumentDescriptions.
 * this might include MaintenanceEvents, or in the case of EAD there might be author/creation data.
 * 
 * @param topLevelUnit the top level DocumentaryUnit to append the maintenanceEvents to (if any)
 * @param itemData the item representation to import, in which to search for maintenanceEvents
 */
//    public abstract void importTopLevelExtraNodes(AbstractUnit topLevelUnit, Map<String, Object> itemData);
}