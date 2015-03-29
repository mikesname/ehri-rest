package eu.ehri.project.models.base;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.PermissionGrant;

/**
 * Interface for entities that can be the targets of permission grants.
 * This is an abstraction over two types of things:
 * <ol>
 *     <li>Types of items (documentaryUnit, repository, etc)</li>
 *     <li>Individual instances of said types</li>
 * </ol>
 */
public interface PermissionGrantTarget extends Frame {
    /**
     * Fetch all permission grants that pertain directly to this target.
     *
     * @return an iterable of permission grant frames
     */
    @Adjacency(label= Ontology.PERMISSION_GRANT_HAS_TARGET, direction=Direction.IN)
    public Iterable<PermissionGrant> getPermissionGrants();
}
