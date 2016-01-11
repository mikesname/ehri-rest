/*
 * Copyright 2015 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen), King's College London,
 * Georg-August-Universitaet Goettingen Stiftung Oeffentlichen Rechts
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.ehri.extension;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.hp.hpl.jena.rdf.model.Model;
import eu.ehri.extension.base.AbstractAccessibleResource;
import eu.ehri.extension.base.CreateResource;
import eu.ehri.extension.base.DeleteResource;
import eu.ehri.extension.base.GetResource;
import eu.ehri.extension.base.ListResource;
import eu.ehri.extension.base.ParentResource;
import eu.ehri.extension.base.UpdateResource;
import eu.ehri.project.core.Tx;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.exceptions.AccessDenied;
import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.exporters.cvoc.JenaSkosExporter;
import eu.ehri.project.importers.cvoc.SkosRDFVocabulary;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.persistence.ActionManager;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.views.impl.CrudViews;
import org.neo4j.graphdb.GraphDatabaseService;

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
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Provides a web service interface for the Vocabulary model. Vocabularies are
 * containers for Concepts.
 */
@Path(Entities.CVOC_VOCABULARY)
public class VocabularyResource extends AbstractAccessibleResource<Vocabulary>
        implements GetResource, ListResource, DeleteResource, CreateResource, UpdateResource, ParentResource {

    public VocabularyResource(@Context GraphDatabaseService database) {
        super(database, Vocabulary.class);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("{id:.+}")
    @Override
    public Response get(@PathParam("id") String id) throws ItemNotFound {
        return getItem(id);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Override
    public Response list() {
        return listItems();
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("{id:.+}/list")
    @Override
    public Response listChildren(
            @PathParam("id") String id,
            @QueryParam(ALL_PARAM) @DefaultValue("false") boolean all) throws ItemNotFound {
        Tx tx = graph.getBaseGraph().beginTx();
        try {
            Accessor user = getRequesterUserProfile();
            Vocabulary vocabulary = views.detail(id, user);
            return streamingPage(getQuery(Concept.class)
                    .page(vocabulary.getConcepts(), user), tx);
        } catch (Exception e) {
            tx.close();
            throw e;
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Override
    public Response create(Bundle bundle,
            @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError, DeserializationError {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            Response item = createItem(bundle, accessors);
            tx.success();
            return item;
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("{id:.+}")
    @Override
    public Response update(@PathParam("id") String id, Bundle bundle)
            throws PermissionDenied, ValidationError,
            DeserializationError, ItemNotFound {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            Response item = updateItem(id, bundle);
            tx.success();
            return item;
        }
    }

    @DELETE
    @Path("{id:.+}")
    @Override
    public Response delete(@PathParam("id") String id)
            throws PermissionDenied, ItemNotFound, ValidationError {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            Response item = deleteItem(id);
            tx.success();
            return item;
        }
    }

    @DELETE
    @Path("/{id:.+}/all")
    public Response deleteAllVocabularyConcepts(@PathParam("id") String id)
            throws ItemNotFound, AccessDenied, PermissionDenied {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            UserProfile user = getCurrentUser();
            Vocabulary vocabulary = views.detail(id, user);
            CrudViews<Concept> conceptViews = new CrudViews<>(
                    graph, Concept.class, vocabulary);
            ActionManager actionManager = new ActionManager(graph, vocabulary);
            Iterable<Concept> concepts = vocabulary.getConcepts();
            if (concepts.iterator().hasNext()) {
                ActionManager.EventContext context = actionManager
                        .newEventContext(user, EventTypes.deletion, getLogMessage());
                for (Concept concept : concepts) {
                    context.addSubjects(concept);
                    conceptViews.delete(concept.getId(), user);
                }
            }
            tx.success();
            return Response.status(Status.OK).build();
        } catch (SerializationError | ValidationError e) {
            throw new RuntimeException(e);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Path("/{id:.+}/" + Entities.CVOC_CONCEPT)
    @Override
    public Response createChild(@PathParam("id") String id,
            Bundle bundle, @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError,
            DeserializationError, ItemNotFound {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            Accessor user = getRequesterUserProfile();
            final Vocabulary vocabulary = views.detail(id, user);
            Response item = createItem(bundle, accessors, new Handler<Concept>() {
                @Override
                public void process(Concept concept) throws PermissionDenied {
                    concept.setVocabulary(vocabulary);
                }
            }, views.setScope(vocabulary).setClass(Concept.class));
            tx.success();
            return item;
        }
    }

    public final static String TURTLE_MIMETYPE = "text/turtle";
    public final static String RDF_XML_MIMETYPE = "application/rdf+xml";
    public final static String N3_MIMETYPE = "application/n-triples";
    public final BiMap<String, String> RDF_MIMETYPE_FORMATS = ImmutableBiMap.of(
            N3_MIMETYPE, "N3",
            TURTLE_MIMETYPE, "TTL",
            RDF_XML_MIMETYPE, "RDF/XML"
    );

    /**
     * Export the given vocabulary as SKOS.
     *
     * @param id      the vocabulary id
     * @param format  the RDF format. Can be one of: RDF/XML, N3, TTL
     * @param baseUri the base URI for exported items
     * @return a SKOS vocabulary
     * @throws IOException
     * @throws ItemNotFound
     */
    @GET
    @Path("{id:.+}/export")
    @Produces({TURTLE_MIMETYPE, RDF_XML_MIMETYPE, N3_MIMETYPE})
    public Response exportSkos(@PathParam("id") String id,
            final @QueryParam("format") String format,
            final @QueryParam("baseUri") String baseUri)
            throws IOException, ItemNotFound {
        final String rdfFormat = getRdfFormat(format, "TTL");
        final String base = baseUri == null ? SkosRDFVocabulary.DEFAULT_BASE_URI : baseUri;
        final MediaType mediaType = MediaType.valueOf(RDF_MIMETYPE_FORMATS
                .inverse().get(rdfFormat));

        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            final Accessor user = getRequesterUserProfile();
            final Vocabulary vocabulary = views.detail(id, user);
            final JenaSkosExporter skosImporter = new JenaSkosExporter(graph, vocabulary);
            final Model model = skosImporter.export(base);
            tx.success();
            return Response.ok(new StreamingOutput() {
                @Override
                public void write(OutputStream outputStream) throws IOException, WebApplicationException {
                    model.getWriter(rdfFormat).write(model, outputStream, base);
                }
            }).type(mediaType + "; charset=utf-8").build();
        }
    }

    private String getRdfFormat(String format, String defaultFormat) {
        if (format == null) {
            for (String mimeValue : RDF_MIMETYPE_FORMATS.keySet()) {
                MediaType mime = MediaType.valueOf(mimeValue);
                if (requestHeaders.getAcceptableMediaTypes().contains(mime)) {
                    return RDF_MIMETYPE_FORMATS.get(mimeValue);
                }
            }
            return defaultFormat;
        } else {
            return RDF_MIMETYPE_FORMATS.containsValue(format) ? format : defaultFormat;
        }
    }
}