package eu.ehri.extension;

import eu.ehri.extension.errors.BadRequester;
import eu.ehri.project.acl.AclManager;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.exceptions.*;
import eu.ehri.project.models.VirtualUnit;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.DescribedEntity;
import eu.ehri.project.models.base.Frame;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.views.Query;
import eu.ehri.project.views.VirtualUnitViews;
import eu.ehri.project.views.impl.LoggingCrudViews;
import org.neo4j.graphdb.GraphDatabaseService;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.List;

/**
 * Provides a Restful interface for the VirtualUnit type
 */
@Path(Entities.VIRTUAL_UNIT)
public final class VirtualUnitResource extends
        AbstractAccessibleEntityResource<VirtualUnit> {

    private final VirtualUnitViews vuViews;
    private final Query<VirtualUnit> virtualUnitQuery;

    public VirtualUnitResource(@Context GraphDatabaseService database) {
        super(database, VirtualUnit.class);
        vuViews = new VirtualUnitViews(graph);
        virtualUnitQuery = new Query<VirtualUnit>(graph, cls);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}")
    public Response getVirtualUnit(@PathParam("id") String id)
            throws ItemNotFound, AccessDenied, BadRequester {
        return retrieve(id);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/list")
    public StreamingOutput listVirtualUnits(
            @QueryParam(OFFSET_PARAM) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit,
            @QueryParam(SORT_PARAM) List<String> order,
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester {
        return list(offset, limit, order, filters);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/page")
    public StreamingOutput pageVirtualUnits(
            @QueryParam(OFFSET_PARAM) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit,
            @QueryParam(SORT_PARAM) List<String> order,
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester {
        return page(offset, limit, order, filters);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/count")
    public Response countVirtualUnits(@QueryParam(FILTER_PARAM) List<String> filters)
            throws ItemNotFound, BadRequester {
        return count(filters);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/list")
    public StreamingOutput listChildVirtualUnits(
            @PathParam("id") String id,
            @QueryParam(OFFSET_PARAM) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit,
            @QueryParam(SORT_PARAM) List<String> order,
            @QueryParam(FILTER_PARAM) List<String> filters,
            @QueryParam(ALL_PARAM) @DefaultValue("false") boolean all)
            throws ItemNotFound, BadRequester, PermissionDenied {
        VirtualUnit parent = manager.getFrame(id, VirtualUnit.class);
        Iterable<VirtualUnit> units = all
                ? parent.getAllChildren()
                : parent.getChildren();
        Query<VirtualUnit> query = virtualUnitQuery
                .setOffset(offset).setLimit(limit).filter(filters)
                .orderBy(order).filter(filters);
        return streamingList(query.list(units, getRequesterUserProfile()));
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/page")
    public StreamingOutput pageChildVirtualUnits(
            @PathParam("id") String id,
            @QueryParam(OFFSET_PARAM) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit,
            @QueryParam(SORT_PARAM) List<String> order,
            @QueryParam(FILTER_PARAM) List<String> filters,
            @QueryParam(ALL_PARAM) @DefaultValue("false") boolean all)
            throws ItemNotFound, BadRequester, PermissionDenied {
        VirtualUnit parent = manager.getFrame(id, VirtualUnit.class);
        Iterable<VirtualUnit> units = all
                ? parent.getAllChildren()
                : parent.getChildren();
        Query<VirtualUnit> query = virtualUnitQuery
                .setOffset(offset).setLimit(limit).filter(filters)
                .orderBy(order).filter(filters);
        return streamingPage(query.page(units, getRequesterUserProfile()));
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/count")
    public Response countChildVirtualUnits(
            @PathParam("id") String id,
            @QueryParam(FILTER_PARAM) List<String> filters,
            @QueryParam(ALL_PARAM) @DefaultValue("false") boolean all)
            throws ItemNotFound, BadRequester, PermissionDenied {
        VirtualUnit parent = manager.getFrame(id, VirtualUnit.class);
        Iterable<VirtualUnit> units = all
                ? parent.getAllChildren()
                : parent.getChildren();
        Query<VirtualUnit> query = virtualUnitQuery.filter(filters);
        return Response.ok((query.count(units,
                getRequesterUserProfile())).toString().getBytes()).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response updateVirtualUnit(String json) throws PermissionDenied,
            IntegrityError, ValidationError, DeserializationError,
            ItemNotFound, BadRequester {
        return update(json);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response createTopLevelVirtualUnit(String json,
            @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError, IntegrityError,
            DeserializationError, ItemNotFound, BadRequester {
        final Accessor currentUser = getCurrentUser();
        return create(json, accessors, new PostProcess() {
            @Override
            public void process(Frame frame) {
                manager.cast(frame, VirtualUnit.class).setAuthor(currentUser);
            }
        });
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}")
    public Response updateVirtualUnit(@PathParam("id") String id,
            String json) throws AccessDenied, PermissionDenied, IntegrityError,
            ValidationError, DeserializationError, ItemNotFound, BadRequester {
        return update(id, json);
    }

    @DELETE
    @Path("/{id:.+}")
    public Response deleteVirtualUnit(@PathParam("id") String id)
            throws AccessDenied, PermissionDenied, ItemNotFound, ValidationError,
            BadRequester, SerializationError {
        return delete(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/" + Entities.VIRTUAL_UNIT)
    public Response createVirtualUnit(@PathParam("id") String id,
            String json, @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws AccessDenied, PermissionDenied, ValidationError, IntegrityError,
            DeserializationError, ItemNotFound, BadRequester {
        Accessor user = getRequesterUserProfile();
        VirtualUnit parent = views.detail(id, user);
        try {
            VirtualUnit doc = createVirtualUnit(json, parent);
            new AclManager(graph).setAccessors(doc,
                    getAccessors(accessors, user));
            graph.getBaseGraph().commit();
            return buildResponseFromVirtualUnit(doc);
        } catch (SerializationError e) {
            graph.getBaseGraph().rollback();
            throw new RuntimeException(e);
        } finally {
            cleanupTransaction();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/for/{id:.+}")
    public StreamingOutput pageVirtualUnitsFor(@PathParam("id") String id,
            @QueryParam(OFFSET_PARAM) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit,
            @QueryParam(SORT_PARAM) List<String> order,
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws AccessDenied, ItemNotFound, BadRequester {
        DescribedEntity item = manager.getFrame(id, DescribedEntity.class);
        Accessor currentUser = getRequesterUserProfile();
        Iterable<VirtualUnit> units = vuViews.getVirtualCollections(item, currentUser);
        Query.Page<VirtualUnit> page = virtualUnitQuery
                .filter(filters).setOffset(offset).setLimit(limit).orderBy(order)
                .page(units, currentUser);
        return streamingPage(page);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/forUser/{userId:.+}")
    public StreamingOutput pageVirtualUnitsForUser(@PathParam("userId") String userId,
            @QueryParam(OFFSET_PARAM) @DefaultValue("0") int offset,
            @QueryParam(LIMIT_PARAM) @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit,
            @QueryParam(SORT_PARAM) List<String> order,
            @QueryParam(FILTER_PARAM) List<String> filters)
            throws AccessDenied, ItemNotFound, BadRequester {
        Accessor accessor = manager.getFrame(userId, Accessor.class);
        Accessor currentUser = getRequesterUserProfile();
        Iterable<VirtualUnit> units = vuViews.getVirtualCollectionsForUser(accessor, currentUser);
        Query.Page<VirtualUnit> page = virtualUnitQuery
                .filter(filters).setOffset(offset).setLimit(limit).orderBy(order)
                .page(units, currentUser);
        return streamingPage(page);
    }

    // Helpers

    private Response buildResponseFromVirtualUnit(VirtualUnit doc)
            throws SerializationError {
        String jsonStr = getSerializer().vertexFrameToJson(doc);

        try {
            // FIXME: Hide the details of building this path
            URI docUri = UriBuilder.fromUri(uriInfo.getBaseUri())
                    .segment(Entities.VIRTUAL_UNIT)
                    .segment(doc.getId()).build();
            return Response.status(Status.CREATED).location(docUri)
                    .entity((jsonStr).getBytes()).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private VirtualUnit createVirtualUnit(String json,
            VirtualUnit parent) throws DeserializationError,
            PermissionDenied, ValidationError, IntegrityError, BadRequester {
        Bundle entityBundle = Bundle.fromString(json);

        Accessor currentUser = getRequesterUserProfile();
        VirtualUnit doc = new LoggingCrudViews<VirtualUnit>(graph,
                VirtualUnit.class, parent).create(entityBundle,
                currentUser, getLogMessage());
        parent.addChild(doc);
        return doc;
    }
}
