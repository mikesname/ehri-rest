package eu.ehri.project.models.base;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.javahandler.JavaHandler;
import com.tinkerpop.frames.modules.javahandler.JavaHandlerContext;
import com.tinkerpop.gremlin.java.GremlinPipeline;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.annotations.Fetch;
import eu.ehri.project.models.events.SystemEvent;
import eu.ehri.project.models.utils.JavaHandlerUtils;

import static eu.ehri.project.models.utils.JavaHandlerUtils.addSingleRelationship;
import static eu.ehri.project.models.utils.JavaHandlerUtils.addUniqueRelationship;
import static eu.ehri.project.models.utils.JavaHandlerUtils.hasEdge;

public interface AccessibleEntity extends PermissionGrantTarget, VersionedEntity, AnnotatableEntity {

    @Fetch(value = Ontology.IS_ACCESSIBLE_TO, ifBelowLevel = 1)
    @Adjacency(label = Ontology.IS_ACCESSIBLE_TO)
    public Iterable<Accessor> getAccessors();

    /**
     * Add an accessor who can access this item. (If there are no
     * accessors the item is globally visible.)
     *
     * NB: This is NOT how to add an item to a group.
     *
     * @param accessor an accessor frame
     */
    @JavaHandler
    public void addAccessor(final Accessor accessor);

    /**
     * Stop an accessor from being able to access this item.
     *
     * @param accessor an accessor frame
     */
    @Adjacency(label = Ontology.IS_ACCESSIBLE_TO)
    public void removeAccessor(final Accessor accessor);

    /**
     * The permission scope to which this item belongs.
     *
     * @return a permission scope frame (or null)
     */
    @Adjacency(label = Ontology.HAS_PERMISSION_SCOPE)
    public PermissionScope getPermissionScope();

    /**
     * Set the permission scope to which this item belongs.
     *
     * @param scope a permission scope frame
     */
    @JavaHandler
    public void setPermissionScope(final PermissionScope scope);

    /**
     * Get all permission scopes to which this item belongs,
     * e.g. immediate parent and all subsequent ancestors.
     *
     * @return an iterable of permission scope frames
     */
    @JavaHandler
    public Iterable<PermissionScope> getPermissionScopes();

    /**
     * Fetch a list of Actions for this entity in order.
     * 
     * @return
     */
    @JavaHandler
    public Iterable<SystemEvent> getHistory();

    /**
     * Get the latest event applying to this item.
     *
     * @return a system event frame, or null
     */
    @Fetch(value = Ontology.ENTITY_HAS_LIFECYCLE_EVENT, ifLevel = 0)
    @JavaHandler
    public SystemEvent getLatestEvent();

    /**
     * Determine if this item is restricted, e.g. if it is limited
     * to one or more accessors.
     *
     * @return whether or not the item is restricted
     */
    @JavaHandler
    boolean hasAccessRestriction();

    /**
     * Implementation of complex methods.
     */
    abstract class Impl implements JavaHandlerContext<Vertex>, AccessibleEntity {

        public void addAccessor(final Accessor accessor) {
            addUniqueRelationship(it(), accessor.asVertex(),
                    Ontology.IS_ACCESSIBLE_TO);
        }

        public void setPermissionScope(final PermissionScope scope) {
            addSingleRelationship(it(), scope.asVertex(),
                    Ontology.HAS_PERMISSION_SCOPE);
        }

        public SystemEvent getLatestEvent() {
            GremlinPipeline<Vertex, Vertex> out = gremlin()
                    .out(Ontology.ENTITY_HAS_LIFECYCLE_EVENT)
                    .out(Ontology.ENTITY_HAS_EVENT);
            return (SystemEvent)(out.hasNext() ? frame(out.next()) : null);
        }

        public Iterable<PermissionScope> getPermissionScopes() {
            return frameVertices(gremlin().as("n")
                    .out(Ontology.HAS_PERMISSION_SCOPE)
                    .loop("n", JavaHandlerUtils.defaultMaxLoops, JavaHandlerUtils.noopLoopFunc));
        }

        public Iterable<SystemEvent> getHistory() {
            return frameVertices(gremlin().as("n").out(Ontology.ENTITY_HAS_LIFECYCLE_EVENT)
                    .loop("n", JavaHandlerUtils.noopLoopFunc, JavaHandlerUtils.noopLoopFunc)
                    .out(Ontology.ENTITY_HAS_EVENT));
        }

        public boolean hasAccessRestriction() {
            return hasEdge(it(), Direction.OUT, Ontology.IS_ACCESSIBLE_TO)
                    && !hasEdge(it(), Direction.OUT, Ontology.PROMOTED_BY);
        }
    }
}
