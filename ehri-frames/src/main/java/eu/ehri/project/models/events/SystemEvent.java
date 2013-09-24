package eu.ehri.project.models.events;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.modules.javahandler.JavaHandler;
import com.tinkerpop.frames.modules.javahandler.JavaHandlerContext;
import com.tinkerpop.pipes.PipeFunction;
import com.tinkerpop.pipes.branch.LoopPipe;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.annotations.Fetch;
import eu.ehri.project.models.annotations.Mandatory;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.models.base.Frame;
import eu.ehri.project.models.utils.JavaHandlerUtils;

@EntityType(EntityClass.SYSTEM_EVENT)
public interface SystemEvent extends AccessibleEntity {

    public static enum EventType {
        lifecycleEvent, interactionEvent
    }

    @Mandatory
    @Property(Ontology.EVENT_TIMESTAMP)
    public String getTimestamp();

    @Mandatory
    @Property(Ontology.EVENT_TYPE)
    public String getEventType();

    @Property(Ontology.EVENT_LOG_MESSAGE)
    public String getLogMessage();

    @Fetch(Ontology.EVENT_HAS_ACTIONER)
    @JavaHandler
    public Iterable<Actioner> getActioners();

    @JavaHandler
    public Iterable<AccessibleEntity> getSubjects();

    @Property(Ontology.EVENT_PRIOR_VERSION)
    public String getPrior();

    @Property(Ontology.EVENT_PRIOR_VERSION)
    public void setPrior(Object prior);

    /**
     * Fetch the "scope" of this event, or the context in which a
     * given creation/modification/deletion event is happening.
     * @return
     */
    @Fetch(value = Ontology.EVENT_HAS_SCOPE, ifDepth = 0)
    @Adjacency(label = Ontology.EVENT_HAS_SCOPE, direction = Direction.OUT)
    public Frame getEventScope();

    @Adjacency(label = Ontology.EVENT_HAS_SCOPE, direction = Direction.OUT)
    public void setEventScope(final Frame frame);

    /**
     * Implementation of complex methods.
     */
    abstract class Impl implements JavaHandlerContext<Vertex>, SystemEvent {
        public Iterable<AccessibleEntity> getSubjects() {
            return frameVertices(gremlin().in(Ontology.ENTITY_HAS_EVENT)
                    .as("n").in(Ontology.ENTITY_HAS_LIFECYCLE_EVENT)
                    .loop("n", JavaHandlerUtils.noopLoopFunc, new PipeFunction<LoopPipe.LoopBundle<Vertex>, Boolean>() {
                        @Override
                        public Boolean compute(LoopPipe.LoopBundle<Vertex> vertexLoopBundle) {
                            return !vertexLoopBundle.getObject().getVertices(Direction.IN,
                                    Ontology.ENTITY_HAS_LIFECYCLE_EVENT).iterator().hasNext();
                        }
                    }));
        }

        public Iterable<Actioner> getActioners() {
            return frameVertices(gremlin().in(Ontology.ENTITY_HAS_EVENT)
                    .as("n").in(Ontology.ACTIONER_HAS_LIFECYCLE_ACTION)
                    .loop("n", JavaHandlerUtils.noopLoopFunc, new PipeFunction<LoopPipe.LoopBundle<Vertex>, Boolean>() {
                        @Override
                        public Boolean compute(LoopPipe.LoopBundle<Vertex> vertexLoopBundle) {
                            return !vertexLoopBundle.getObject().getVertices(Direction.IN,
                                    Ontology.ACTIONER_HAS_LIFECYCLE_ACTION).iterator().hasNext();
                        }
                    }));
        }
    }
}
