package eu.ehri.project.models.base;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.javahandler.JavaHandler;
import com.tinkerpop.frames.modules.javahandler.JavaHandlerContext;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.Group;
import eu.ehri.project.models.PermissionGrant;
import eu.ehri.project.models.utils.JavaHandlerUtils;

/**
 * Interface frame representing an entity that can access
 * resources.
 */
public interface Accessor extends IdentifiableEntity {

    /**
     * Determine if this accessor is the admin group.
     *
     * @return whether or not the item is admin
     */
    @JavaHandler
    public boolean isAdmin();

    /**
     * Determine if this accessor is anonymous.
     *
     * @return whether or not the item is anonymous
     */
    @JavaHandler
    public boolean isAnonymous();

    /**
     * Get all parent accessors to which this one belongs,
     * e.g. groups to which this item (a user or a group)
     * belongs.
     *
     * @return an iterable of accessor frames
     */
    @Adjacency(label = Ontology.ACCESSOR_BELONGS_TO_GROUP)
    public Iterable<Accessor> getParents();

    /**
     * Get all ancestor accessors to which this one belongs,
     * to all depths.
     *
     * @return an iterable of accessor frames
     */
    @JavaHandler
    public Iterable<Accessor> getAllParents();

    /**
     * Get permission grants that apply to this accessor.
     *
     * @return an iterable of permission grant frames
     */
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_SUBJECT, direction=Direction.IN)
    public Iterable<PermissionGrant> getPermissionGrants();

    /**
     * Set this accessor as the subject of a permission grant.
     *
     * @param grant a permission grant item
     */
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_SUBJECT, direction=Direction.IN)
    public void addPermissionGrant(final PermissionGrant grant);

    abstract class Impl implements JavaHandlerContext<Vertex>, Accessor {

        public boolean isAdmin() {
            return it().getProperty(Ontology.IDENTIFIER_KEY).equals(Group.ADMIN_GROUP_IDENTIFIER);
        }

        public boolean isAnonymous() {
            return false;
        }

        public Iterable<Accessor> getAllParents() {
            return frameVertices(gremlin().as("n")
                    .out(Ontology.ACCESSOR_BELONGS_TO_GROUP)
                    .loop("n", JavaHandlerUtils.defaultMaxLoops, JavaHandlerUtils.noopLoopFunc));
        }
    }
}
