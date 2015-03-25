package eu.ehri.project.models;

import com.tinkerpop.frames.Adjacency;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.annotations.Fetch;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.Frame;
import eu.ehri.project.models.base.PermissionGrantTarget;
import eu.ehri.project.models.base.PermissionScope;

/**
 * Frame class representing a grant of a permission
 * to a user.
 *
 * A grant can have an optional scope to limit its applicability
 * to item's within a particular permission scope, e.g. a CREATE
 * grant that only applies to content type "documentaryUnit" within
 * a specific repository.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
@EntityType(EntityClass.PERMISSION_GRANT)
public interface PermissionGrant extends Frame {

    /**
     * Fetch the subject of this grant, e.g. the user or group to whom
     * it applies.
     *
     * @return an accessor frame
     */
    @Fetch(value = Ontology.PERMISSION_GRANT_HAS_SUBJECT, ifBelowLevel = 1, numLevels = 1)
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_SUBJECT)
    public Accessor getSubject();

    /**
     * Fetch the accessor who granted this permission.
     *
     * @return an accessor frame
     */
    @Fetch(value = Ontology.PERMISSION_GRANT_HAS_GRANTEE, ifBelowLevel = 1, numLevels = 1)
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_GRANTEE)
    public Accessor getGrantee();

    /**
     * Fetch the target of this grant, e.g. a content type, or a specific content item.
     *
     * @return a permission grant target frame
     */
    @Fetch(value = Ontology.PERMISSION_GRANT_HAS_TARGET, ifBelowLevel = 1, numLevels = 1)
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_TARGET)
    public Iterable<PermissionGrantTarget> getTargets();

    /**
     * Add a target to this grant.
     *
     * @param target an additional target item
     */
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_TARGET)
    public void addTarget(final PermissionGrantTarget target);

    /**
     * Get the permission to which this grant applies.
     *
     * @return a permission frame
     */
    @Fetch(Ontology.PERMISSION_GRANT_HAS_PERMISSION)
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_PERMISSION)
    public Permission getPermission();

    /**
     * Set the permission to which this grant applies.
     *
     * @param permission a permission frame
     */
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_PERMISSION)
    public void setPermission(final Permission permission);

    /**
     * Get the (optional) scope to which this grant applies, meaning the
     * permission is only granted when the target is an item that has
     * this item as a parent (or ancestor scope).
     *
     * @return the scope of the permission grant, or null
     */
    @Fetch(value = Ontology.PERMISSION_GRANT_HAS_SCOPE, ifBelowLevel = 1, numLevels = 0)
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_SCOPE)
    public PermissionScope getScope();

    /**
     * Set the scope of this grant, meaning it only applies when the
     * target belongs to this item's permission scope.
     *
     * @param scope a permission scope frame
     */
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_SCOPE)
    public void setScope(final PermissionScope scope);

    /**
     * Remove the scope from the permission grant.
     *
     * @param scope the grant's current scope
     */
    @Adjacency(label = Ontology.PERMISSION_GRANT_HAS_SCOPE)
    public void removeScope(final PermissionScope scope);
}
