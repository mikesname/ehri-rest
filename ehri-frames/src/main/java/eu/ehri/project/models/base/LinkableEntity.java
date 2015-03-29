package eu.ehri.project.models.base;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.Link;

/**
 * An entity that can hold incoming links.
 */
public interface LinkableEntity extends AccessibleEntity {
    /**
     * Links which refer to this item as a target.
     *
     * @return an iterable of link frames
     */
    @Adjacency(label = Ontology.LINK_HAS_TARGET, direction = Direction.IN)
    public Iterable<Link> getLinks();

    /**
     * Add a link to this target.
     *
     * @param link a link frame
     */
    @Adjacency(label = Ontology.LINK_HAS_TARGET, direction = Direction.IN)
    public void addLink(final Link link);
}
