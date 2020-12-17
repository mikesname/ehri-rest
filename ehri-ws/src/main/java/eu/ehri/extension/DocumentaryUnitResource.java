/*
 * Copyright 2020 Data Archiving and Networked Services (an institute of
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

import com.google.common.collect.Lists;
import eu.ehri.extension.base.*;
import eu.ehri.project.core.Tx;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exceptions.*;
import eu.ehri.project.exporters.ead.Ead2002Exporter;
import eu.ehri.project.importers.json.BatchOperations;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.base.Entity;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.tools.IdRegenerator;
import eu.ehri.project.utils.Table;
import org.neo4j.graphdb.GraphDatabaseService;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.transform.TransformerException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Provides a web service interface for the DocumentaryUnit model.
 */
@Path(AbstractResource.RESOURCE_ENDPOINT_PREFIX + "/" + Entities.DOCUMENTARY_UNIT)
public class DocumentaryUnitResource
        extends AbstractAccessibleResource<DocumentaryUnit>
        implements GetResource, ListResource, UpdateResource, ParentResource, DeleteResource {

    public DocumentaryUnitResource(@Context GraphDatabaseService database) {
        super(database, DocumentaryUnit.class);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id:[^/]+}")
    @Override
    public Response get(@PathParam("id") String id) throws ItemNotFound {
        return getItem(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response list() {
        return listItems();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id:[^/]+}/list")
    @Override
    public Response listChildren(
            @PathParam("id") String id,
            @QueryParam(ALL_PARAM) @DefaultValue("false") boolean all) throws ItemNotFound {
        try (final Tx tx = beginTx()) {
            DocumentaryUnit parent = manager.getEntity(id, DocumentaryUnit.class);
            Response response = streamingPage(() -> {
                Iterable<DocumentaryUnit> units = all
                        ? parent.getAllChildren()
                        : parent.getChildren();
                return getQuery().page(units, cls);
            });
            tx.success();
            return response;
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id:[^/]+}")
    @Override
    public Response update(@PathParam("id") String id,
            Bundle bundle) throws PermissionDenied,
            ValidationError, DeserializationError, ItemNotFound {
        try (final Tx tx = beginTx()) {
            Response response = updateItem(id, bundle);
            tx.success();
            return response;
        }
    }

    @DELETE
    @Path("{id:[^/]+}")
    @Override
    public void delete(@PathParam("id") String id)
            throws PermissionDenied, ItemNotFound, ValidationError {
        try (final Tx tx = beginTx()) {
            deleteItem(id);
            tx.success();
        }
    }

    @POST
    @Path("{id:[^/]+}/rename/{to:[^/]+}")
    public Table rename(@PathParam("id") String id, @PathParam("to") String newIdentifier)
            throws PermissionDenied, ItemNotFound, ValidationError, SerializationError,
            DeserializationError, IdRegenerator.IdCollisionError {
        try (final Tx tx = beginTx()) {
            IdRegenerator idGen = new IdRegenerator(graph).withActualRename(true);
            DocumentaryUnit entity = api().detail(id, DocumentaryUnit.class);
            Bundle newBundle = getSerializer()
                    .withDependentOnly(true)
                    .entityToBundle(entity)
                    .withDataValue(Ontology.IDENTIFIER_KEY, newIdentifier);
            api().update(newBundle, DocumentaryUnit.class, getLogMessage());
            List<List<String>> renamed = Lists.newArrayList();
            idGen.reGenerateId(entity).ifPresent(renamed::add);
            for (DocumentaryUnit child : entity.getAllChildren()) {
                idGen.reGenerateId(child).ifPresent(renamed::add);
            }
            tx.success();
            return Table.of(renamed);
        }
    }

    @DELETE
    @Path("{id:[^/]+}/all")
    public void deleteAll(@PathParam("id") String id) throws ItemNotFound, PermissionDenied, ValidationError {
        try (final Tx tx = beginTx()) {
            deleteItem(id, item -> {
                // Delete the children first. If this fails due to permission
                // errors the transaction won't be committed.
                List<String> ids = StreamSupport.stream(
                        item.getAllChildren().spliterator(), false)
                        .map(Entity::getId)
                        .collect(Collectors.toList());
                try {
                    new BatchOperations(graph)
                            .setScope(item)
                            .setVersioning(true)
                            .batchDelete(ids, getCurrentActioner(), getLogMessage());
                } catch (ItemNotFound e) {
                    throw new RuntimeException(e);
                }
            });
            tx.success();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id:[^/]+}")
    @Override
    public Response createChild(@PathParam("id") String id,
            Bundle bundle, @QueryParam(ACCESSOR_PARAM) List<String> accessors)
            throws PermissionDenied, ValidationError,
            DeserializationError, ItemNotFound {
        try (final Tx tx = beginTx()) {
            final DocumentaryUnit parent = api().detail(id, cls);
            Response resource = createItem(bundle, accessors,
                    parent::addChild,
                    api().withScope(parent), cls);
            tx.success();
            return resource;
        }
    }

    /**
     * Export the given documentary unit as EAD.
     *
     * @param id   the unit id
     * @param lang a three-letter ISO639-2 code
     * @return an EAD XML Document
     */
    @GET
    @Path("{id:[^/]+}/ead")
    @Produces(MediaType.TEXT_XML)
    public Response exportEad(@PathParam("id") String id,
            final @QueryParam(LANG_PARAM) @DefaultValue(DEFAULT_LANG) String lang)
            throws ItemNotFound {
        try (final Tx tx = beginTx()) {
            DocumentaryUnit unit = api().detail(id, cls);
            tx.success();
            return Response.ok((StreamingOutput) outputStream -> {
                try (final Tx tx2 = beginTx()) {
                    new Ead2002Exporter(api()).export(unit, outputStream, lang);
                    tx2.success();
                } catch (TransformerException e) {
                    throw new WebApplicationException(e);
                }
            }).type(MediaType.TEXT_XML + "; charset=utf-8").build();
        }
    }
}
