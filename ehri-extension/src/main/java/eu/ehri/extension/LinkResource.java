package eu.ehri.extension;

import eu.ehri.extension.base.DeleteResource;
import eu.ehri.extension.base.GetResource;
import eu.ehri.extension.base.UpdateResource;
import eu.ehri.extension.errors.BadRequester;
import eu.ehri.project.acl.PermissionType;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.exceptions.AccessDenied;
import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.UndeterminedRelationship;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.Actioner;
import eu.ehri.project.models.base.DescribedEntity;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.LinkableEntity;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.views.DescriptionViews;
import eu.ehri.project.views.LinkViews;
import eu.ehri.project.views.Query;
import org.neo4j.graphdb.GraphDatabaseService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;

/**
 * Provides a web service interface for creating/reading item links.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Path(Entities.LINK)
public class LinkResource extends AbstractAccessibleEntityResource<Link>
        implements GetResource, DeleteResource, UpdateResource {

    public static final String BODY_PARAM = "body";

    private final LinkViews linkViews;
    private final DescriptionViews<DescribedEntity> descriptionViews;

    public LinkResource(@Context GraphDatabaseService database) {
        super(database, Link.class);
        linkViews = new LinkViews(graph);
        descriptionViews = new DescriptionViews<DescribedEntity>(graph, DescribedEntity.class);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}")
    @Override
    public Response get(@PathParam("id") String id) throws ItemNotFound,
            AccessDenied, BadRequester {
        return getItem(id);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/list")
    public Response list() throws BadRequester {
        return listItems();
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/count")
    public long countResources() throws BadRequester {
        return countItems();
    }

    @PUT
    @Path("/{id:.+}")
    @Override
    public Response update(@PathParam("id") String id, Bundle bundle)
            throws AccessDenied, PermissionDenied, ItemNotFound, ValidationError,
            BadRequester, DeserializationError {
        return updateItem(id, bundle);
    }

    @PUT
    @Override
    public Response update(Bundle bundle)
            throws PermissionDenied, ItemNotFound, ValidationError,
            BadRequester, DeserializationError {
        return updateItem(bundle);
    }

    /**
     * Create a link between two items.
     *
     * @param targetId  The link target
     * @param sourceId  The link source
     * @param bundle      The link body data
     * @param bodies    optional list of entities to provide the body
     * @param accessors The IDs of accessors who can see this link
     * @return The created link item
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws DeserializationError
     * @throws ItemNotFound
     * @throws BadRequester
     * @throws SerializationError
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("{targetId:.+}/{sourceId:.+}")
    public Response createLinkFor(
            @PathParam("targetId") String targetId,
            @PathParam("sourceId") String sourceId, Bundle bundle,
            @QueryParam(BODY_PARAM) List<String> bodies,
            @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError, DeserializationError,
            ItemNotFound, BadRequester, SerializationError {
        Accessor user = getRequesterUserProfile();
        try {
            Link link = linkViews.createLink(targetId,
                    sourceId, bodies, bundle, user);
            aclManager.setAccessors(link,
                    getAccessors(accessors, user));
            graph.getBaseGraph().commit();
            return creationResponse(link);
        } catch (SerializationError e) {
            graph.getBaseGraph().rollback();
            throw new RuntimeException(e);
        } finally {
            cleanupTransaction();
        }

    }

    /**
     * Delete an access point.
     */
    @DELETE
    @Path("/accessPoint/{id:.+}")
    public Response deleteAccessPoint(@PathParam("id") String id)
            throws AccessDenied, PermissionDenied, ItemNotFound, ValidationError,
            BadRequester, SerializationError {
        graph.getBaseGraph().checkNotInTransaction();
        try {
            Accessor userProfile = getRequesterUserProfile();
            UndeterminedRelationship rel = manager.getFrame(id, UndeterminedRelationship.class);
            Description description = rel.getDescription();
            if (description == null) {
                throw new ItemNotFound(id);
            }
            DescribedEntity item = description.getEntity();
            if (item == null) {
                throw new ItemNotFound(id);
            }
            descriptionViews.delete(item.getId(), id, userProfile, getLogMessage());
            graph.getBaseGraph().commit();
            return Response.status(Status.OK).build();
        } finally {
            cleanupTransaction();
        }
    }

    /**
     * Returns a list of items linked to the given description.
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/for/{id:.+}")
    public Response listRelatedItems(@PathParam("id") String id)
            throws ItemNotFound, BadRequester {
        Query<Link> linkQuery = new Query<Link>(graph, Link.class)
                .setStream(isStreaming());
        return streamingPage(linkQuery.setStream(isStreaming()).page(
                manager.getFrame(id, LinkableEntity.class).getLinks(),
                getRequesterUserProfile()));
    }

    /**
     * Delete a link. If the optional ?accessPoint=[ID] parameter is also given
     * the access point associated with the link will also be deleted.
     */
    @DELETE
    @Path("/for/{id:.+}/{linkId:.+}")
    public Response deleteLinkForItem(@PathParam("id") String id, @PathParam("linkId") String linkId)
            throws AccessDenied, PermissionDenied, ItemNotFound, ValidationError,
            BadRequester {
        graph.getBaseGraph().checkNotInTransaction();
        try {
            helper.checkEntityPermission(manager.getFrame(id, AccessibleEntity.class),
                    getRequesterUserProfile(), PermissionType.ANNOTATE);
            Actioner actioner = manager.cast(getRequesterUserProfile(), Actioner.class);
            Link link = manager.getFrame(linkId, EntityClass.LINK, Link.class);
            actionManager.logEvent(link, actioner, EventTypes.deletion);
            manager.deleteVertex(link.asVertex());
            graph.getBaseGraph().commit();
            return Response.ok().build();
        } finally {
            cleanupTransaction();
        }
    }

    /**
     * Delete a link.
     */
    @DELETE
    @Path("/{id:.+}")
    @Override
    public Response delete(@PathParam("id") String id)
            throws AccessDenied, PermissionDenied, ItemNotFound, ValidationError,
            BadRequester {
        return deleteItem(id);
    }
}
