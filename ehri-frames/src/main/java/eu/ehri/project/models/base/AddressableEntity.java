package eu.ehri.project.models.base;

import com.tinkerpop.frames.Adjacency;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.Address;
import eu.ehri.project.models.annotations.Dependent;
import eu.ehri.project.models.annotations.Fetch;

/**
 * @author Linda Reijnhoudt (https://github.com/lindareijnhoudt)
 */
public interface AddressableEntity {

    /**
     * Fetch all addresses belonging to this item.
     *
     * @return an iterable of address frames
     */
    @Dependent
    @Fetch(value = Ontology.ENTITY_HAS_ADDRESS, whenNotLite = true)
    @Adjacency(label = Ontology.ENTITY_HAS_ADDRESS)
    public Iterable<Address> getAddresses();
}
