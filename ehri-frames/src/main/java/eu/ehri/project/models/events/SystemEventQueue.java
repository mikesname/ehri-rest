package eu.ehri.project.models.events;

import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.modules.javahandler.JavaHandler;
import com.tinkerpop.frames.modules.javahandler.JavaHandlerContext;
import com.tinkerpop.pipes.util.Pipeline;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.base.Frame;
import eu.ehri.project.models.utils.JavaHandlerUtils;

/**
 * Class representing the system event queue node, of which
 * there Will Be Only One.
 *
 * The head of the event queue is a sort of anchor to the
 * most recent event, which attach to it as a newest-first
 * linked list.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
@EntityType(EntityClass.SYSTEM)
public interface SystemEventQueue extends Frame {

    public static final String STREAM_START = Ontology.ACTIONER_HAS_LIFECYCLE_ACTION + "Stream";

    /**
     * An iterable of all events in the system.
     *
     * @return an iterable of system event frames
     */
    @JavaHandler
    public Iterable<SystemEvent> getSystemEvents();

    abstract class Impl implements JavaHandlerContext<Vertex>, SystemEventQueue {
        public Iterable<SystemEvent> getSystemEvents() {
            Pipeline<Vertex,Vertex> otherPipe = gremlin().as("n")
                    .out(Ontology.ACTIONER_HAS_LIFECYCLE_ACTION)
                    .loop("n", JavaHandlerUtils.noopLoopFunc, JavaHandlerUtils.noopLoopFunc);
            return frameVertices(gremlin()
                    .out(STREAM_START).cast(Vertex.class)
                    .copySplit(gremlin(), otherPipe)
                    .exhaustMerge().cast(Vertex.class));
        }
    }
}
