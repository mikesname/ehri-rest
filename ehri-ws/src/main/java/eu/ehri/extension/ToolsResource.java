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

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.tinkerpop.blueprints.CloseableIterable;
import com.tinkerpop.blueprints.Vertex;
import eu.ehri.project.core.Tx;
import eu.ehri.project.core.impl.neo4j.Neo4j2Vertex;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.exceptions.*;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.DescribedEntity;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.models.idgen.GenericIdGenerator;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.persistence.Serializer;
import eu.ehri.project.tools.FindReplace;
import eu.ehri.project.tools.IdRegenerator;
import eu.ehri.project.tools.Linker;
import org.neo4j.graphdb.GraphDatabaseService;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Miscellaneous additional functionality that doesn't
 * fit anywhere else.
 */
@Path(ToolsResource.ENDPOINT)
public class ToolsResource extends AbstractRestResource {

    private final FindReplace findReplace;
    private final Linker linker;

    public static final String ENDPOINT = "tools";
    public static final String TOLERANT_PARAM = "tolerant";
    public static final String SINGLE_PARAM = "single";
    public static final String LANG_PARAM = "lang";
    public static final String ACCESS_POINT_TYPE_PARAM = "apt";
    public static final String DEFAULT_LANG = "eng";

    public ToolsResource(@Context GraphDatabaseService database) {
        super(database);
        findReplace = new FindReplace(graph);
        linker = new Linker(graph);
    }

    /**
     * Create concepts and links derived from the access points
     * on a repository's documentary units.
     *
     * @param repositoryId     the repository id
     * @param vocabularyId     the target vocabulary
     * @param languageCode     the language code of created concepts
     * @param accessPointTypes the access point types to process
     * @param tolerant         proceed even if there are integrity errors due
     *                         to slug collisions in the created concepts
     * @param excludeSingle    don't create concepts/links for access points that
     *                         are unique to a single item
     * @return the number of links created
     * @throws ItemNotFound
     * @throws ValidationError
     * @throws PermissionDenied
     * @throws DeserializationError
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/generateConcepts/{repositoryId:.+}/{vocabularyId:.+}")
    public long autoLinkRepositoryDocs(
            @PathParam("repositoryId") String repositoryId,
            @PathParam("vocabularyId") String vocabularyId,
            @QueryParam(ACCESS_POINT_TYPE_PARAM) List<String> accessPointTypes,
            @QueryParam(LANG_PARAM) @DefaultValue(DEFAULT_LANG) String languageCode,
            @QueryParam(SINGLE_PARAM) @DefaultValue("true") boolean excludeSingle,
            @QueryParam(TOLERANT_PARAM) @DefaultValue("false") boolean tolerant)
            throws ItemNotFound, ValidationError,
            PermissionDenied, DeserializationError {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            UserProfile user = getCurrentUser();
            Repository repository = manager.getFrame(repositoryId, Repository.class);
            Vocabulary vocabulary = manager.getFrame(vocabularyId, Vocabulary.class);

            long linkCount = linker
                    .withAccessPointTypes(accessPointTypes)
                    .withTolerant(tolerant)
                    .withExcludeSingles(excludeSingle)
                    .withDefaultLanguage(languageCode)
                    .withLogMessage(getLogMessage())
                    .createAndLinkRepositoryVocabulary(repository, vocabulary, user);

            tx.success();
            return linkCount;
        }
    }

    /**
     * Find an replace a regex-specified substring of a property value
     * across an entire entity class e.g.
     * if an Address has a property with name &quot;url&quot; and value &quot;www.foo.com/bar&quot;,
     * providing a regex value <code>^www</code> and replacement <code>http://www</code> will
     * give the property a value of &quot;http://www.foo.com/bar&quot;.
     * <p>
     * <strong>Warning: This is a sharp tool! Back up the whole database first!</strong>
     *
     * @param entityType The type of entity
     * @param propName   The name of the property to find and replace
     * @param regex      A regex specifying a substring of the property value
     * @param replace    A replacement substring
     * @return How many items have been changed
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/_findReplacePropertyValueRE")
    public long renamePropertyValueRE(
            @QueryParam("type") String entityType,
            @QueryParam("name") String propName,
            @QueryParam("pattern") String regex,
            @QueryParam("replace") String replace) throws Exception {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            EntityClass entityClass = EntityClass.withName(entityType);
            Pattern pattern = Pattern.compile(regex);
            long changes = findReplace
                    .propertyValueRE(entityClass, propName, pattern, replace);
            tx.success();
            return changes;
        }
    }

    /**
     * Change a property key name across an entire entity class.
     * <p>
     * <strong>Warning: This is a sharp tool! Back up the whole database first!</strong>
     *
     * @param entityType The type of entity
     * @param oldKeyName The existing property key name
     * @param newKeyName The new property key name
     * @return The number of items changed
     * @throws Exception
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/_findReplacePropertyName")
    public long renamePropertyName(
            @QueryParam("type") String entityType,
            @QueryParam("from") String oldKeyName,
            @QueryParam("to") String newKeyName) throws Exception {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            EntityClass entityClass = EntityClass.withName(entityType);
            long changes = findReplace
                    .propertyName(entityClass, oldKeyName, newKeyName);
            tx.success();
            return changes;
        }
    }

    /**
     * Find an replace a property value across an entire entity class, e.g.
     * if a DocumentaryUnit has a property with name &quot;foo&quot; and value &quot;bar&quot;,
     * change the value to &quot;baz&quot; on all items.
     * <p>
     * <strong>Warning: This is a sharp tool! Back up the whole database first!</strong>
     *
     * @param entityType The type of entity
     * @param propName   The name of the property to find and replace
     * @param oldValue   The property value to change
     * @param newValue   The new value
     * @return How many items have been changed
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/_findReplacePropertyValue")
    public long renamePropertyValue(
            @QueryParam("type") String entityType,
            @QueryParam("name") String propName,
            @QueryParam("from") String oldValue,
            @QueryParam("to") String newValue) throws Exception {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            EntityClass entityClass = EntityClass.withName(entityType);
            long changes = findReplace
                    .propertyValue(entityClass, propName, oldValue, newValue);
            tx.success();
            return changes;
        }
    }

    /**
     * Regenerate the hierarchical graph ID for a given item, optionally
     * renaming it.
     * <p>
     * The default mode is to output items whose IDs would change, without
     * actually changing them. The {@code collisions} parameter will <b>only</b>
     * output items that would cause collisions if renamed, whereas {@code tolerant}
     * mode will skip them altogether.
     * <p>
     * The {@code commit} flag will cause renaming to take place.
     *
     * @param id         the item's existing ID
     * @param collisions only output items that if renamed to the
     *                   regenerated ID would cause collisions
     * @param tolerant   skip items that could cause collisions rather
     *                   then throwing an error
     * @param commit     whether or not to rename the item
     * @return a tab old-to-new mapping, or an empty
     * body if nothing was changed
     * @throws ItemNotFound
     * @throws IOException
     */
    @POST
    @Produces("text/csv")
    @Path("/_regenerateId/{id:.+}")
    public String regenerateId(
            @PathParam("id") String id,
            @QueryParam("collisions") @DefaultValue("false") boolean collisions,
            @QueryParam("tolerant") @DefaultValue("false") boolean tolerant,
            @QueryParam("commit") @DefaultValue("false") boolean commit)
            throws ItemNotFound, IOException, IdRegenerator.IdCollisionError {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            AccessibleEntity item = manager.getFrame(id, AccessibleEntity.class);
            Optional<List<String>> remap = new IdRegenerator(graph)
                    .withActualRename(commit)
                    .collisionMode(collisions)
                    .skippingCollisions(tolerant)
                    .reGenerateId(item);
            tx.success();
            return makeCsv(Lists.newArrayList(remap.asSet()));
        }
    }

    /**
     * Regenerate the hierarchical graph ID all items of a given
     * type.
     * <p>
     * The default mode is to output items whose IDs would change, without
     * actually changing them. The {@code collisions} parameter will <b>only</b>
     * output items that would cause collisions if renamed, whereas {@code tolerant}
     * mode will skip them altogether.
     * <p>
     * The {@code commit} flag will cause renaming to take place.
     *
     * @param type       the item type
     * @param collisions only output items that if renamed to the
     *                   regenerated ID would cause collisions
     * @param tolerant   skip items that could cause collisions rather
     *                   then throwing an error
     * @param commit     whether or not to rename the items
     * @return a tab list old-to-new mappings, or an empty
     * body if nothing was changed
     * @throws IOException
     */
    @POST
    @Produces("text/csv")
    @Path("/_regenerateIdsForType/{type:.+}")
    public String regenerateIdsForType(
            @PathParam("type") String type,
            @QueryParam("collisions") @DefaultValue("false") boolean collisions,
            @QueryParam("tolerant") @DefaultValue("false") boolean tolerant,
            @QueryParam("commit") @DefaultValue("false") boolean commit)
            throws IOException, IdRegenerator.IdCollisionError {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            EntityClass entityClass = EntityClass.withName(type);
            try (CloseableIterable<AccessibleEntity> frames = manager
                    .getFrames(entityClass, AccessibleEntity.class)) {
                List<List<String>> lists = new IdRegenerator(graph)
                        .withActualRename(commit)
                        .collisionMode(collisions)
                        .skippingCollisions(tolerant)
                        .reGenerateIds(frames);
                tx.success();
                return makeCsv(lists);
            }
        }
    }

    /**
     * Regenerate the hierarchical graph ID for all items within the
     * permission scope and lower levels.
     * <p>
     * The default mode is to output items whose IDs would change, without
     * actually changing them. The {@code collisions} parameter will <b>only</b>
     * output items that would cause collisions if renamed, whereas {@code tolerant}
     * mode will skip them altogether.
     * <p>
     * The {@code commit} flag will cause renaming to take place.
     *
     * @param scopeId    the scope item's ID
     * @param collisions only output items that if renamed to the
     *                   regenerated ID would cause collisions
     * @param tolerant   skip items that could cause collisions rather
     *                   then throwing an error
     * @param commit     whether or not to rename the items
     * @return a tab list old-to-new mappings, or an empty
     * body if nothing was changed
     * @throws ItemNotFound
     * @throws IOException
     */
    @POST
    @Produces("text/csv")
    @Path("/_regenerateIdsForScope/{scope:.+}")
    public String regenerateIdsForScope(
            @PathParam("scope") String scopeId,
            @QueryParam("collisions") @DefaultValue("false") boolean collisions,
            @QueryParam("tolerant") @DefaultValue("false") boolean tolerant,
            @QueryParam("commit") @DefaultValue("false") boolean commit)
            throws IOException, ItemNotFound, IdRegenerator.IdCollisionError {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            PermissionScope scope = manager.getFrame(scopeId, PermissionScope.class);
            List<List<String>> lists = new IdRegenerator(graph)
                    .withActualRename(commit)
                    .skippingCollisions(tolerant)
                    .collisionMode(collisions)
                    .reGenerateIds(scope.getAllContainedItems());
            tx.success();
            return makeCsv(lists);
        }
    }

    /**
     * Regenerate description IDs.
     *
     * @throws ItemNotFound
     * @throws IOException
     */
    @POST
    @Produces("text/plain")
    @Path("/_regenerateDescriptionIds")
    public String regenerateDescriptionIds(
            @QueryParam("buffer") @DefaultValue("-1") int bufferSize,
            @QueryParam("commit") @DefaultValue("false") boolean commit)
            throws IOException, ItemNotFound, IdRegenerator.IdCollisionError {
        EntityClass[] types = {EntityClass.DOCUMENTARY_UNIT_DESCRIPTION, EntityClass
                .CVOC_CONCEPT_DESCRIPTION, EntityClass.HISTORICAL_AGENT_DESCRIPTION, EntityClass
                .REPOSITORY_DESCRIPTION};
        Serializer depSerializer = new Serializer.Builder(graph).dependentOnly().build();
        int done = 0;
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            for (EntityClass entityClass : types) {
                try (CloseableIterable<Description> descriptions = manager.getFrames(entityClass, Description.class)) {
                    for (Description desc : descriptions) {
                        DescribedEntity entity = desc.getEntity();
                        if (entity != null) {
                            PermissionScope scope = entity.getPermissionScope();
                            List<String> idPath = scope != null
                                    ? Lists.newArrayList(scope.idPath())
                                    : Lists.<String>newArrayList();
                            idPath.add(entity.getIdentifier());
                            Bundle descBundle = depSerializer.vertexFrameToBundle(desc);
                            String newId = entityClass.getIdGen().generateId(idPath, descBundle);
                            if (!newId.equals(desc.getId()) && commit) {
                                manager.renameVertex(desc.asVertex(), desc.getId(), newId);
                                done++;

                                if (bufferSize > 0 && done % bufferSize == 0) {
                                    tx.success();
                                }
                            }
                        }
                    }
                }
            }
            if (commit && done > 0) {
                tx.success();
            }
            return String.valueOf(done);
        } catch (SerializationError e) {
            throw new RuntimeException(e);
        }
    }

    @POST
    @Produces("text/plain")
    @Path("/_setIdsOnEventLinks")
    public String setIdsOnEventLinks()
            throws IOException, ItemNotFound, IdRegenerator.IdCollisionError {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            long done = 0;
            for (Vertex v : graph.getVertices()) {
                if ("EventLink".equals(v.getProperty("_debugType"))) {
                    String id = v.getProperty(EntityType.ID_KEY);
                    if (id == null) {
                        UUID timeBasedUUID = GenericIdGenerator.getTimeBasedUUID();
                        v.setProperty(EntityType.ID_KEY, timeBasedUUID.toString());
                        v.setProperty(EntityType.TYPE_KEY, Entities.EVENT_LINK);
                        done++;

                        if (done % 10000 == 0) {
                            graph.getBaseGraph().commit();
                        }
                    }
                }
            }
            tx.success();
            return String.valueOf(done);
        }
    }

    @POST
    @Produces("text/plain")
    @Path("/_setLabels")
    public String setLabels()
            throws IOException, ItemNotFound, IdRegenerator.IdCollisionError {
        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            long done = 0;
            for (Vertex v : graph.getVertices()) {
                Neo4j2Vertex neo4j2Vertex = (Neo4j2Vertex)v;
                List<String> labels = Lists.newArrayList(neo4j2Vertex.getLabels());
                for (String label : labels) {
                    neo4j2Vertex.removeLabel(label);
                }
                String type = neo4j2Vertex.getProperty(EntityType.TYPE_KEY);
                if (type != null) {
                    neo4j2Vertex.addLabel(type);
                    done++;
                }

                if (done % 10000 == 0) {
                    graph.getBaseGraph().commit();
                }
            }
            tx.success();
            return String.valueOf(done);
        }
    }

    // Helpers

    private String makeCsv(List<List<String>> rows) throws IOException {
        StringWriter writer = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(writer, '\t', CSVWriter.NO_QUOTE_CHARACTER);
        for (List<String> remap : rows) {
            String[] strings = remap.toArray(new String[2]);
            csvWriter.writeNext(strings);
        }
        csvWriter.close();
        return writer.toString();
    }
}
