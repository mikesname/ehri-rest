package eu.ehri.extension;

import eu.ehri.extension.errors.BadRequester;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.exceptions.*;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.persistence.ActionManager;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.views.Query;
import eu.ehri.project.views.impl.CrudViews;
import eu.ehri.project.views.impl.LoggingCrudViews;
import org.neo4j.graphdb.GraphDatabaseService;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;

/**
 * Provides a RESTful interface for the Vocabulary Also for managing the
 * Concepts that are in the Vocabulary
 *
 * @author paulboon
 */
@Path(Entities.CVOC_VOCABULARY)
public class VocabularyResource extends
        AbstractAccessibleEntityResource<Vocabulary> {

    public VocabularyResource(@Context GraphDatabaseService database) {
        super(database, Vocabulary.class);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}")
    public Response getVocabulary(@PathParam("id") String id)
            throws ItemNotFound, AccessDenied, BadRequester {
        return retrieve(id);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/count")
    public Response countVocabularies(@QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester {
        return count(filters);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/list")
    public Response listVocabularies(
            @QueryParam(PAGE_PARAM) @DefaultValue("1") int page,
            @QueryParam(COUNT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int count,
            @QueryParam(SORT_PARAM) List<String> order,
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester {
        return page(page, count, order, filters);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/count")
    public Response countVocabularyConcepts(
            @PathParam("id") String id,
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester, AccessDenied {
        Accessor user = getRequesterUserProfile();
        Vocabulary vocabulary = views.detail(id, user);
        Query<Concept> query = new Query<Concept>(graph, Concept.class)
                .filter(filters);
        return Response.ok((query.count(vocabulary.getConcepts(), user))
                .toString().getBytes()).build();
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/list")
    public Response listVocabularyConcepts(
            @PathParam("id") String id,
            @QueryParam(PAGE_PARAM) @DefaultValue("1") int page,
            @QueryParam(COUNT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int count,
            @QueryParam(SORT_PARAM) List<String> order,
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester, AccessDenied {
        Accessor user = getRequesterUserProfile();
        Vocabulary vocabulary = views.detail(id, user);
        Query<Concept> query = new Query<Concept>(graph, Concept.class)
                .setCount(count).setPage(page).orderBy(order)
                .filter(filters)
                .setStream(isStreaming());
        return streamingPage(query.page(vocabulary.getConcepts(), user));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response createVocabulary(String json,
            @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError, IntegrityError,
            DeserializationError, ItemNotFound, BadRequester {
        return create(json, accessors);
    }

    // Note: json contains id
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response updateVocabulary(String json) throws PermissionDenied,
            IntegrityError, ValidationError, DeserializationError,
            ItemNotFound, BadRequester {
        return update(json);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}")
    public Response updateVocabulary(@PathParam("id") String id, String json)
            throws AccessDenied, PermissionDenied, IntegrityError, ValidationError,
            DeserializationError, ItemNotFound, BadRequester {
        return update(id, json);
    }

    @DELETE
    @Path("/{id:.+}")
    public Response deleteVocabulary(@PathParam("id") String id)
            throws AccessDenied, PermissionDenied, ItemNotFound, ValidationError,
            BadRequester {
        return delete(id);
    }

    /**
     * Concept manipulation **
     */

    @DELETE
    @Path("/{id:.+}/all")
    public Response deleteAllVocabularyConcepts(@PathParam("id") String id)
            throws ItemNotFound, BadRequester, AccessDenied, PermissionDenied {
        try {
            UserProfile user = getCurrentUser();
            Vocabulary vocabulary = views.detail(id, user);
            CrudViews<Concept> conceptViews = new CrudViews<Concept>(
                    graph, Concept.class, vocabulary);
            ActionManager actionManager = new ActionManager(graph, vocabulary);
            Iterable<Concept> concepts = vocabulary.getConcepts();
            if (concepts.iterator().hasNext()) {
                ActionManager.EventContext context = actionManager
                        .logEvent(user, EventTypes.deletion, getLogMessage());
                for (Concept concept : concepts) {
                    context.addSubjects(concept);
                    conceptViews.delete(concept.getId(), user);
                }
            }
            graph.getBaseGraph().commit();
            return Response.status(Status.OK).build();
        } catch (SerializationError e) {
            graph.getBaseGraph().rollback();
            throw new RuntimeException(e);
        } catch (ValidationError e) {
            graph.getBaseGraph().rollback();
            throw new RuntimeException(e);
        } finally {
            cleanupTransaction();
        }
    }

    /**
     * Create a top-level concept unit for this vocabulary.
     *
     * @param id   The vocabulary ID
     * @param json The new concept data
     * @return The new concept
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws IntegrityError
     * @throws DeserializationError
     * @throws ItemNotFound
     * @throws BadRequester
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/" + Entities.CVOC_CONCEPT)
    public Response createVocabularyConcept(@PathParam("id") String id,
            String json, @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError, IntegrityError,
            DeserializationError, ItemNotFound, BadRequester {
        Accessor user = getRequesterUserProfile();
        Vocabulary vocabulary = views.detail(id, user);
        try {
            Concept concept = createConcept(json, vocabulary);
            aclManager.setAccessors(concept,
                    getAccessors(accessors, user));
            graph.getBaseGraph().commit();
            return creationResponse(concept);
        } catch (SerializationError e) {
            graph.getBaseGraph().rollback();
            throw new RuntimeException(e);
        } finally {
            cleanupTransaction();
        }
    }

    // Helpers

    private Concept createConcept(String json, Vocabulary vocabulary)
            throws DeserializationError, PermissionDenied, ValidationError,
            IntegrityError, BadRequester {
        Bundle entityBundle = Bundle.fromString(json);

        Concept concept = new LoggingCrudViews<Concept>(graph, Concept.class,
                vocabulary).create(entityBundle, getRequesterUserProfile(), getLogMessage());

        // Add it to this Vocabulary's concepts
        concept.setVocabulary(vocabulary);
        return concept;
    }

}
