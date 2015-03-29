package eu.ehri.project.models.base;

/**
 * Interface frame for entities that contain one or more
 * other entities of the same or different types, in a
 * hierarchical relationship.
 *
 * e.g. repository->documentaryUnit
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
public interface ItemHolder {
    public static final String CHILD_COUNT = "childCount";

    /**
     * Get a count of the number of items held by this item.
     *
     * @return a numeric count
     */
    public long getChildCount();
}
