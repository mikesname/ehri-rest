package eu.ehri.project.models.base;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.Annotation;

/**
 * Frame interface representing something that can be annotated.
 */
public interface AnnotatableEntity extends Frame {
    /**
     * Get all annotations for this item.
     *
     * @return an iterable of annotation frames
     */
    @Adjacency(label = Ontology.ANNOTATION_ANNOTATES, direction = Direction.IN)
    public Iterable<Annotation> getAnnotations();

    /**
     * Add an annotation to this item.
     *
     * @param annotation an annotation frame
     */
    @Adjacency(label = Ontology.ANNOTATION_ANNOTATES, direction = Direction.IN)
    public void addAnnotation(final Annotation annotation);

    /**
     * If this item is part of another item (e.g. a description or a date period)
     * mark this item as being the part to which an annotation refers, when the
     * annotation itself refers broadly to the parent item.
     *
     * @param annotation an annotation frame
     */
    @Adjacency(label = Ontology.ANNOTATION_ANNOTATES_PART, direction = Direction.IN)
    public void addAnnotationPart(final Annotation annotation);
}
