package eu.ehri.project.models.base;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.annotations.Dependent;
import eu.ehri.project.models.annotations.Fetch;

/**
 * Interface representing items which can be multiply described.
 */
public interface DescribedEntity extends PermissionScope, AnnotatableEntity, LinkableEntity {

    /**
     * Add a description to this entity.
     *
     * @param description a description frame
     */
    @Adjacency(label = Ontology.DESCRIPTION_FOR_ENTITY, direction = Direction.IN)
    public void addDescription(final Description description);

    /**
     * Remove a description from this entity.
     *
     * @param description an existing description frame
     */
    @Adjacency(label = Ontology.DESCRIPTION_FOR_ENTITY, direction = Direction.IN)
    public void removeDescription(final Description description);

    /**
     * Fetch all descriptions for this item.
     *
     * @return an iterable of description frames
     */
    @Fetch(Ontology.DESCRIPTION_FOR_ENTITY)
    @Dependent
    @Adjacency(label = Ontology.DESCRIPTION_FOR_ENTITY, direction = Direction.IN)
    public Iterable<Description> getDescriptions();
}
