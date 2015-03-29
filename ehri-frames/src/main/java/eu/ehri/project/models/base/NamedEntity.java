package eu.ehri.project.models.base;

import com.tinkerpop.frames.Property;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.annotations.Mandatory;

/**
 * An entity which must have a mandatory name property.
 */
public interface NamedEntity extends Frame {

    /**
     * Fetch the name for this item.
     *
     * @return a name string
     */
    @Mandatory
    @Property(Ontology.NAME_KEY)
    public String getName();
}
