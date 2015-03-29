package eu.ehri.project.models.base;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.MaintenanceEvent;
import eu.ehri.project.models.UndeterminedRelationship;
import eu.ehri.project.models.UnknownProperty;
import eu.ehri.project.models.annotations.Dependent;
import eu.ehri.project.models.annotations.Fetch;
import eu.ehri.project.models.annotations.Mandatory;

/**
 * Interface frame for descriptions of entities. Descriptions contain
 * metadata about items in a particular language.
 */
public interface Description extends NamedEntity, AccessibleEntity {

    /**
     * Process by which this description was created. Currently supported
     * values allow for automatic import or manual creation (by a human).
     */
    public static enum CreationProcess {
        MANUAL, IMPORT
    }

    /**
     * Fetch the entity described by this description.
     *
     * @return a described entity frame
     */
    @Adjacency(label = Ontology.DESCRIPTION_FOR_ENTITY)
    public DescribedEntity getEntity();

    /**
     * Get the ISO-639-2 code for the language in which this description
     * is described.
     *
     * @return a three-letter ISO639-2 code
     */
    @Mandatory
    @Property(Ontology.LANGUAGE_OF_DESCRIPTION)
    public String getLanguageOfDescription();

    /**
     * Get the (optional) identifier code for this description.
     * The description code may uniquely identify the source of
     * a description, or disambiguate multiple descriptions that
     * share the same language code.
     *
     * @return a string identifier, or null
     */
    @Property(Ontology.IDENTIFIER_KEY)
    public String getDescriptionCode();

    /**
     * Get the creation process for this description, which,
     * currently, can be either IMPORT (ingested automatically)
     * or MANUAL (created manually).
     *
     * @return the creation process code
     */
    @Property(Ontology.CREATION_PROCESS)
    public CreationProcess getCreationProcess();

    /**
     * Get the described entity of a description. This 
     * method if @Fetch serialized only if the description
     * is at the top level of the requested subtree.
     */
    @Fetch(value = Ontology.DESCRIPTION_FOR_ENTITY, ifLevel =0)
    @Adjacency(label = Ontology.DESCRIPTION_FOR_ENTITY)
    public DescribedEntity getDescribedEntity();

    /**
     * Get all maintenance events that pertain to this description.
     *
     * @return an iterable of maintenance events
     */
    @Dependent
    @Fetch(value = Ontology.HAS_MAINTENANCE_EVENT, whenNotLite = true)
    @Adjacency(label = Ontology.HAS_MAINTENANCE_EVENT, direction=Direction.IN)
    public abstract Iterable<MaintenanceEvent> getMaintenanceEvents();

    /**
     * Set the list of maintenance events for this item.
     *
     * @param maintenanceEvents an iterable of maintenance events.
     */
    @Adjacency(label = Ontology.HAS_MAINTENANCE_EVENT, direction=Direction.IN)
    public abstract void setMaintenanceEvents(final Iterable<MaintenanceEvent> maintenanceEvents);

    /**
     * Add a maintenance event to this description.
     *
     * @param maintenanceEvent a maintenance event item.
     */
    @Adjacency(label = Ontology.HAS_MAINTENANCE_EVENT, direction=Direction.IN)
    public abstract void addMaintenanceEvent(final MaintenanceEvent maintenanceEvent);

    @Dependent
    @Fetch(value = Ontology.HAS_ACCESS_POINT, whenNotLite = true)
    @Adjacency(label = Ontology.HAS_ACCESS_POINT)
    public Iterable<UndeterminedRelationship> getUndeterminedRelationships();

    @Adjacency(label = Ontology.HAS_ACCESS_POINT)
    public void setUndeterminedRelationships(final Iterable<UndeterminedRelationship> relationship);

    @Adjacency(label = Ontology.HAS_ACCESS_POINT)
    public void addUndeterminedRelationship(final UndeterminedRelationship relationship);

    @Dependent
    @Fetch(value = Ontology.HAS_UNKNOWN_PROPERTY, ifLevel = 1, whenNotLite = true)
    @Adjacency(label = Ontology.HAS_UNKNOWN_PROPERTY)
    public Iterable<UnknownProperty> getUnknownProperties();
}
