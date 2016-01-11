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

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.hp.hpl.jena.shared.NoReaderForLangException;
import eu.ehri.extension.base.AbstractRestResource;
import eu.ehri.project.core.Tx;
import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.AbstractImporter;
import eu.ehri.project.importers.managers.CsvImportManager;
import eu.ehri.project.importers.EacHandler;
import eu.ehri.project.importers.EacImporter;
import eu.ehri.project.importers.EadHandler;
import eu.ehri.project.importers.EadImporter;
import eu.ehri.project.importers.EagHandler;
import eu.ehri.project.importers.EagImporter;
import eu.ehri.project.importers.ImportLog;
import eu.ehri.project.importers.managers.ImportManager;
import eu.ehri.project.importers.managers.SaxImportManager;
import eu.ehri.project.importers.SaxXmlHandler;
import eu.ehri.project.importers.cvoc.SkosImporter;
import eu.ehri.project.importers.cvoc.SkosImporterFactory;
import eu.ehri.project.importers.exceptions.InputParseError;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.models.cvoc.Vocabulary;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Resource class for import endpoints.
 */
@Path(ImportResource.ENDPOINT)
public class ImportResource extends AbstractRestResource {

    public static final String ENDPOINT = "import";

    private static final Logger logger = LoggerFactory.getLogger(ImportResource.class);
    private static final String DEFAULT_EAD_HANDLER = EadHandler.class.getName();
    private static final String DEFAULT_EAD_IMPORTER = EadImporter.class.getName();

    public static final String LOG_PARAM = "log";
    public static final String SCOPE_PARAM = "scope";
    public static final String TOLERANT_PARAM = "tolerant";
    public static final String HANDLER_PARAM = "handler";
    public static final String IMPORTER_PARAM = "importer";
    public static final String PROPERTIES_PARAM = "properties";
    public static final String FORMAT_PARAM = "format";

    public static final String CSV_MEDIA_TYPE = "text/csv";

    public ImportResource(@Context GraphDatabaseService database) {
        super(database);
    }


    /**
     * Import a SKOS file, of varying formats, as specified by the &quot;language&quot;
     * column of the file extensions table <a href="https://jena.apache.org/documentation/io/">here</a>.
     * <p>
     * Example:
     * <p>
     * <pre>
     * {@code
     * curl -X POST \
     *      -H "X-User: mike" \
     *      --data-binary @skos-data.rdf \
     *      "http://localhost:7474/ehri/import/skos?scope=gb-my-vocabulary&log=testing&tolerant=true"
     * }
     * </pre>
     *
     * @param scopeId    The id of the import scope (i.e. repository)
     * @param tolerant   Whether or not to die on the first validation error
     * @param logMessage Log message for import. If this refers to an accessible local file
     *                   its contents will be used.
     * @param format     The RDF format of the POSTed data
     * @param stream     A stream of SKOS data in a valid format.
     * @return A JSON object showing how many records were created,
     * updated, or unchanged.
     */
    @POST
//    @Consumes({"application/rdf+xml","text/turtle","application/n-triples","application/trig","application/n-quads","application/ld+json"})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("skos")
    public Response importSkos(
            @QueryParam(SCOPE_PARAM) String scopeId,
            @DefaultValue("false") @QueryParam(TOLERANT_PARAM) Boolean tolerant,
            @QueryParam(LOG_PARAM) String logMessage,
            @QueryParam(FORMAT_PARAM) String format,
            InputStream stream)
            throws ItemNotFound, ValidationError,
            IOException, DeserializationError {

        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            // Get the current user from the Authorization header and the scope
            // from the query params...
            UserProfile user = getCurrentUser();
            Vocabulary scope = manager.getEntity(scopeId, Vocabulary.class);
            SkosImporter importer = SkosImporterFactory.newSkosImporter(graph, user, scope);

            ImportLog log = importer
                    .setFormat(format)
                    .setTolerant(tolerant)
                    .importFile(stream, getLogMessage(logMessage).orNull());

            tx.success();
            return Response.ok(jsonMapper.writeValueAsBytes(log.getData())).build();
        } catch (InputParseError e) {
            throw new DeserializationError("Unable to parse input: " + e.getMessage());
        } catch (NoReaderForLangException e) {
            throw new DeserializationError("Unable to read language: " + format);
        }
    }

    /**
     * Import a set of EAD files. The POST body can be one of:
     * <ul>
     * <li>a single EAD file</li>
     * <li>multiple EAD files in an archive</li>
     * <li>a plain test file containing local file paths</li>
     * </ul>
     * The Content-Type header is used to distinguish the contents.
     * <br>
     * <b>Note:</b> The archive does not currently support compression.
     * <p>
     * The way you would run with would typically be:
     * <p>
     * <pre>
     * {@code
     *     curl -X POST \
     *      -H "X-User: mike" \
     *      --data-binary @ead-list.txt \
     *      "http://localhost:7474/ehri/import/ead?scope=my-repo-id&log=testing&tolerant=true"
     *
     * # NB: Data is sent using --data-binary to preserve line-breaks - otherwise
     * # it needs url encoding.
     * }
     * </pre>
     * <p>
     * (Assuming <code>ead-list.txt</code> is a list of newline separated EAD file paths.)
     * <p>
     * (TODO: Might be better to use a different way of encoding the local file paths...)
     *
     * @param scopeId       The id of the import scope (i.e. repository)
     * @param tolerant      Whether or not to die on the first validation error
     * @param logMessage    Log message for import. If this refers to an accessible local file
     *                      its contents will be used.
     * @param handlerClass  The fully-qualified handler class name
     *                      (defaults to EadHandler)
     * @param importerClass The fully-qualified import class name
     *                      (defaults to EadImporter)
     * @param propertyFile  A local file path pointing to an import properties
     *                      configuration file.
     * @param data          File data containing one of: a single EAD file,
     *                      multiple EAD files in an archive, a list of local file
     *                      paths. The Content-Type header is used to distinguish
     *                      the contents.
     * @return A JSON object showing how many records were created,
     * updated, or unchanged.
     */
    @POST
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML,
            MediaType.TEXT_XML, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("ead")
    public Response importEad(
            @QueryParam(SCOPE_PARAM) String scopeId,
            @DefaultValue("false") @QueryParam(TOLERANT_PARAM) Boolean tolerant,
            @QueryParam(LOG_PARAM) String logMessage,
            @QueryParam(PROPERTIES_PARAM) String propertyFile,
            @QueryParam(HANDLER_PARAM) String handlerClass,
            @QueryParam(IMPORTER_PARAM) String importerClass,
            InputStream data)
            throws ItemNotFound, ValidationError,
            IOException, DeserializationError {

        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            checkPropertyFile(propertyFile);
            Class<? extends SaxXmlHandler> handler
                    = getHandlerCls(handlerClass, DEFAULT_EAD_HANDLER);
            Class<? extends AbstractImporter> importer
                    = getImporterCls(importerClass, DEFAULT_EAD_IMPORTER);

            // Get the current user from the Authorization header and the scope
            // from the query params...
            UserProfile user = getCurrentUser();
            PermissionScope scope = manager.getEntity(scopeId, PermissionScope.class);

            // Run the import!
            String message = getLogMessage(logMessage).orNull();
            ImportManager importManager = new SaxImportManager(graph, scope, user, importer, handler)
                    .withProperties(propertyFile)
                    .setTolerant(tolerant);
            ImportLog log = importDataStream(importManager, message, data,
                    MediaType.APPLICATION_XML_TYPE, MediaType.TEXT_XML_TYPE);
            tx.success();
            return Response.ok(jsonMapper.writeValueAsBytes(log.getData())).build();
        } catch (ClassNotFoundException e) {
            throw new DeserializationError("Class not found: " + e.getMessage());
        } catch (IllegalArgumentException | InputParseError | ArchiveException e) {
            throw new DeserializationError(e.getMessage());
        }
    }

    /**
     * Import EAG files. See EAD import for details.
     */
    @POST
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML,
            MediaType.TEXT_XML, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("eag")
    public Response importEag(
            @QueryParam(SCOPE_PARAM) String scopeId,
            @DefaultValue("false") @QueryParam(TOLERANT_PARAM) Boolean tolerant,
            @QueryParam(LOG_PARAM) String logMessage,
            @QueryParam(PROPERTIES_PARAM) String propertyFile,
            @QueryParam(HANDLER_PARAM) String handlerClass,
            @QueryParam(IMPORTER_PARAM) String importerClass,
            InputStream data)
            throws ItemNotFound, ValidationError, IOException, DeserializationError {
        return importEad(scopeId, tolerant, logMessage, propertyFile,
                nameOrDefault(handlerClass, EagHandler.class.getName()),
                nameOrDefault(importerClass, EagImporter.class.getName()), data);
    }

    /**
     * Import EAC files. See EAD import for details.
     */
    @POST
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML,
            MediaType.TEXT_XML, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("eac")
    public Response importEac(
            @QueryParam(SCOPE_PARAM) String scopeId,
            @DefaultValue("false") @QueryParam(TOLERANT_PARAM) Boolean tolerant,
            @QueryParam(LOG_PARAM) String logMessage,
            @QueryParam(PROPERTIES_PARAM) String propertyFile,
            @QueryParam(HANDLER_PARAM) String handlerClass,
            @QueryParam(IMPORTER_PARAM) String importerClass,
            InputStream data)
            throws ItemNotFound, ValidationError, IOException, DeserializationError {
        return importEad(scopeId, tolerant, logMessage, propertyFile,
                nameOrDefault(handlerClass, EacHandler.class.getName()),
                nameOrDefault(importerClass, EacImporter.class.getName()), data);
    }

    /**
     * Import a set of CSV files. See EAD handler for options and
     * defaults but substitute text/csv for the input mimetype when
     * a single file is POSTed.
     * <p>
     * Additional note: no handler class is required.
     */
    @POST
    @Consumes({MediaType.TEXT_PLAIN, CSV_MEDIA_TYPE,
            MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("csv")
    public Response importCsv(
            @QueryParam(SCOPE_PARAM) String scopeId,
            @QueryParam(LOG_PARAM) String logMessage,
            @QueryParam(IMPORTER_PARAM) String importerClass,
            InputStream data)
            throws ItemNotFound, ValidationError,
            IOException, DeserializationError {

        try (final Tx tx = graph.getBaseGraph().beginTx()) {
            Class<? extends AbstractImporter> importer
                    = getImporterCls(importerClass, DEFAULT_EAD_IMPORTER);

            // Get the current user from the Authorization header and the scope
            // from the query params...
            UserProfile user = getCurrentUser();
            PermissionScope scope = manager.getEntity(scopeId, PermissionScope.class);

            // Run the import!
            String message = getLogMessage(logMessage).orNull();
            ImportManager importManager = new CsvImportManager(graph, scope, user, importer);
            ImportLog log = importDataStream(importManager, message, data,
                    MediaType.valueOf(CSV_MEDIA_TYPE));
            tx.success();
            return Response.ok(jsonMapper.writeValueAsBytes(log.getData())).build();
        } catch (InputParseError ex) {
            throw new DeserializationError("ParseError: " + ex.getMessage());
        } catch (ClassNotFoundException e) {
            throw new DeserializationError("Class not found: " + e.getMessage());
        } catch (IllegalArgumentException | ArchiveException e) {
            throw new DeserializationError(e.getMessage());
        }
    }

    // Helpers

    private ImportLog importDataStream(ImportManager importManager, String message, InputStream data,
            MediaType... accepts) throws IOException, ValidationError, InputParseError, ArchiveException {
        MediaType mediaType = requestHeaders.getMediaType();
        if (MediaType.TEXT_PLAIN_TYPE.equals(mediaType)) {
            // Extract our list of paths...
            List<String> paths = getFilePaths(IOUtils.toString(data, StandardCharsets.UTF_8));
            return importManager
                    .importFiles(paths, message);
        } else if (Lists.newArrayList(accepts).contains(mediaType)) {
            return importManager.importFile(data, message);
        } else {
            return importArchive(importManager, message, data);
        }
    }

    private ImportLog importArchive(ImportManager importManager, String logMessage, InputStream data)
            throws IOException, ValidationError, ArchiveException, InputParseError {
        logger.info("Import via compressed archive...");
        try (BufferedInputStream bis = new BufferedInputStream(data);
             ArchiveInputStream archiveInputStream = new
                     ArchiveStreamFactory().createArchiveInputStream(bis)) {
            return importManager
                    .importFiles(archiveInputStream, logMessage);
        }
    }

    private static List<String> getFilePaths(String pathList) {
        List<String> files = Lists.newArrayList();
        for (String path : Splitter.on("\n").omitEmptyStrings().trimResults().split(pathList)) {
            if (!new File(path).exists()) {
                throw new IllegalArgumentException("File specified in payload not found: " + path);
            }
            files.add(path);
        }
        return files;
    }

    private static void checkPropertyFile(String properties) {
        // Null properties are allowed
        if (properties != null) {
            File file = new File(properties);
            if (!(file.isFile() && file.exists())) {
                throw new IllegalArgumentException("Properties file '" + properties + "' " +
                        "either does not exist, or is not a file.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends SaxXmlHandler> getHandlerCls(String handlerName, String
            defaultHandler)
            throws ClassNotFoundException, DeserializationError {
        String name = nameOrDefault(handlerName, defaultHandler);
        Class<?> handler = Class.forName(name);
        if (!SaxXmlHandler.class.isAssignableFrom(handler)) {
            throw new DeserializationError("Class '" + handlerName + "' is" +
                    " not an instance of " + SaxXmlHandler.class.getSimpleName());
        }
        return (Class<? extends SaxXmlHandler>) handler;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AbstractImporter> getImporterCls(String importerName, String defaultImporter)
            throws ClassNotFoundException, DeserializationError {
        String name = nameOrDefault(importerName, defaultImporter);
        Class<?> importer = Class.forName(name);
        if (!AbstractImporter.class.isAssignableFrom(importer)) {
            throw new DeserializationError("Class '" + importerName + "' is" +
                    " not an instance of " + AbstractImporter.class.getSimpleName());
        }
        return (Class<? extends AbstractImporter>) importer;
    }

    private Optional<String> getLogMessage(String logMessagePathOrText) throws IOException {
        if (logMessagePathOrText == null || logMessagePathOrText.trim().isEmpty()) {
            return getLogMessage();
        } else {
            File fileTest = new File(logMessagePathOrText);
            if (fileTest.exists()) {
                return Optional.of(FileUtils.readFileToString(fileTest, "UTF-8"));
            } else {
                return Optional.of(logMessagePathOrText);
            }
        }
    }

    private static String nameOrDefault(String name, String defaultName) {
        return (name == null || name.trim().isEmpty()) ? defaultName : name;
    }
}