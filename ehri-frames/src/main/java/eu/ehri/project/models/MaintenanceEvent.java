package eu.ehri.project.models;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.annotations.Mandatory;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.TemporalEntity;

/**
 * Frame class representing a pre-ingest event that took place
 * upon some documentary unit item.
 *
 * @author Linda Reijnhoudt (https://github.com/lindareijnhoudt)
 */
@EntityType(EntityClass.MAINTENANCE_EVENT)
public interface MaintenanceEvent extends TemporalEntity, AccessibleEntity {
     public static final String EVENTTYPE = "eventType";
     public static final String AGENTTYPE = "agentType";
     public enum EventType { CREATED, REVISED }
     public enum AgentType { HUMAN }
     
    //TODO: decide whether to make these required
    @Mandatory
    @Property(EVENTTYPE)
    public void setEventType(EventType eventType);

    @Property(AGENTTYPE)
    public void setAgentType(AgentType agentType);

    //not required
    @Adjacency(label = Ontology.HISTORICAL_AGENT_CREATED, direction = Direction.IN)
    public Iterable<HistoricalAgent> getCreators();

    @Adjacency(label = Ontology.HISTORICAL_AGENT_CREATED, direction = Direction.IN)
    public void addCreator(final HistoricalAgent creator);
}
