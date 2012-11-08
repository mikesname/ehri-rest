package eu.ehri.extension;

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Response.Status;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityTypes;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.persistance.EntityBundle;
import eu.ehri.project.views.ActionViews;
import eu.ehri.project.views.Query;

/**
 * Provides a RESTfull interface for the DocumentaryUnit
 */
@Path(EntityTypes.DOCUMENTARY_UNIT)
public class DocumentaryUnitResource extends
        EhriNeo4jFramedResource<DocumentaryUnit> {

    public DocumentaryUnitResource(@Context GraphDatabaseService database) {
        super(database, DocumentaryUnit.class);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:\\d+}")
    public Response getDocumentaryUnit(@PathParam("id") long id)
            throws PermissionDenied {
        return retrieve(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:.+}")
    public Response getDocumentaryUnit(@PathParam("id") String id)
            throws ItemNotFound, PermissionDenied {
        return retrieve(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    public StreamingOutput listDocumentaryUnits(
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit) {
        return list(offset, limit);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateDocumentaryUnit(String json) throws PermissionDenied,
            IntegrityError, ValidationError, DeserializationError {
        return update(json);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:.+}")
    public Response updateDocumentaryUnit(@PathParam("id") String id,
            String json) throws PermissionDenied, IntegrityError,
            ValidationError, DeserializationError, ItemNotFound {
        return update(id, json);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteDocumentaryUnit(@PathParam("id") long id)
            throws PermissionDenied, ValidationError {
        return delete(id);
    }

    @DELETE
    @Path("/{id:.+}")
    public Response deleteDocumentaryUnit(@PathParam("id") String id)
            throws PermissionDenied, ItemNotFound, ValidationError {
        return delete(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:.+}/" + EntityTypes.DOCUMENTARY_UNIT)
    public Response createAgentDocumentaryUnit(@PathParam("id") String id,
            String json) throws PermissionDenied, ValidationError,
            IntegrityError, DeserializationError, ItemNotFound {
        Transaction tx = graph.getBaseGraph().getRawGraph().beginTx();
        try {
            DocumentaryUnit parent = new Query<DocumentaryUnit>(graph,
                    DocumentaryUnit.class).get(AccessibleEntity.IDENTIFIER_KEY,
                    id, getRequesterUserProfileId());
            DocumentaryUnit doc = createDocumentaryUnit(json, parent);
            tx.success();
            return buildResponseFromDocumentaryUnit(doc);
        } catch (SerializationError e) {
            tx.failure();
            throw new WebApplicationException(e);
        } finally {
            tx.finish();
        }
    }

    // Helpers

    private Response buildResponseFromDocumentaryUnit(DocumentaryUnit doc)
            throws SerializationError {
        String jsonStr = converter.vertexFrameToJson(doc);

        try {
            URI docUri = UriBuilder.fromUri(uriInfo.getBaseUri())
                    .segment(EntityTypes.DOCUMENTARY_UNIT)
                    .segment(doc.getIdentifier()).build();
            return Response.status(Status.CREATED).location(docUri)
                    .entity((jsonStr).getBytes()).build();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            throw new RuntimeException(e);
        }
    }

    private DocumentaryUnit createDocumentaryUnit(String json,
            DocumentaryUnit parent) throws DeserializationError,
            PermissionDenied, ValidationError, IntegrityError {
        EntityBundle<DocumentaryUnit> entityBundle = converter
                .jsonToBundle(json);

        DocumentaryUnit doc = new ActionViews<DocumentaryUnit>(graph,
                DocumentaryUnit.class).create(
                converter.bundleToData(entityBundle),
                getRequesterUserProfileId());
        // Add it to this agent's collections
        parent.addChild(doc);
        parent.getAgent().addCollection(doc);
        return doc;
    }
}
