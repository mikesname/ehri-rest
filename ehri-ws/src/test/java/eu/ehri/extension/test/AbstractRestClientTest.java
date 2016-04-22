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

package eu.ehri.extension.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import eu.ehri.extension.base.AbstractRestResource;
import eu.ehri.extension.providers.BundleProvider;
import eu.ehri.extension.providers.GlobalPermissionSetProvider;
import eu.ehri.extension.providers.ImportLogProvider;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.persistence.BundleDeserializer;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.net.URL;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sun.jersey.api.client.ClientResponse.Status.OK;

/**
 * Base class for testing the REST interface on a 'embedded' neo4j server.
 */
public class AbstractRestClientTest extends RunningServerTest {

    protected static final Client client;

    static {
        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(GlobalPermissionSetProvider.class);
        config.getClasses().add(BundleProvider.class);
        config.getClasses().add(ImportLogProvider.class);
        client = Client.create(config);
    }

    protected static final ObjectMapper jsonMapper = new ObjectMapper();

    static {
        SimpleModule bundleModule = new SimpleModule();
        bundleModule.addDeserializer(Bundle.class, new BundleDeserializer());
        jsonMapper.registerModule(bundleModule);
    }

    protected static final Pattern paginationPattern = Pattern.compile("offset=(-?\\d+); limit=(-?\\d+); total=(-?\\d+)");

    // Admin user prefix - depends on fixture data
    final static private String adminUserProfileId = "mike";

    // Regular user
    final static private String regularUserProfileId = "reto";

    protected String getAdminUserProfileId() {
        return adminUserProfileId;
    }

    protected String getRegularUserProfileId() {
        return regularUserProfileId;
    }

    /**
     * Helpers **
     */

    protected List<Bundle> getItemList(URI uri, String userId) throws Exception {
        return getItemList(uri, userId, new MultivaluedMapImpl());
    }

    /**
     * Get a list of items at some url, as the given user.
     */
    protected List<Bundle> getItemList(URI uri, String userId,
            MultivaluedMap<String, String> params) throws Exception {
        TypeReference<LinkedList<Bundle>> typeRef = new TypeReference<LinkedList<Bundle>>() {
        };
        return jsonMapper.readValue(getJson(uri, userId, params), typeRef);
    }

    protected List<List<Bundle>> getItemListOfLists(URI uri, String userId) throws Exception {
        return getItemListOfLists(uri, userId, new MultivaluedMapImpl());
    }

    /**
     * Get a list of items at some relativeUrl, as the given user.
     */
    protected List<List<Bundle>> getItemListOfLists(URI uri, String userId,
            MultivaluedMap<String, String> params) throws Exception {
        TypeReference<LinkedList<LinkedList<Bundle>>> typeRef = new
                TypeReference<LinkedList<LinkedList<Bundle>>>() {
                };
        return jsonMapper.readValue(getJson(uri, userId, params), typeRef);
    }

    /**
     * Function for fetching a list of entities with the given EntityType
     */
    protected List<Bundle> getEntityList(String entityType, String userId)
            throws Exception {
        return getEntityList(entityUri(entityType), userId, new MultivaluedMapImpl());
    }

    /**
     * Get an item as a bundle object.
     *
     * @param type the item's type
     * @param id   the items ID
     * @return a bundle
     * @throws Exception
     */
    protected Bundle getEntity(String type, String id, String userId) throws Exception {
        return jsonMapper.readValue(getJson(
                entityUri(type, id), userId, new MultivaluedMapImpl()), Bundle.class);
    }

    /**
     * Function for fetching a list of entities with the given EntityType,
     * and some additional parameters.
     */
    protected List<Bundle> getEntityList(URI uri,
            String userId, MultivaluedMap<String, String> params) throws Exception {
        return getItemList(uri, userId, params);
    }

    protected Integer getPaginationTotal(ClientResponse response) {
        MultivaluedMap<String, String> headers = response.getHeaders();
        String range = headers.getFirst("Content-Range");
        if (range != null && range.matches(paginationPattern.pattern())) {
            Matcher matcher = paginationPattern.matcher(range);
            return matcher.find() ? Integer.valueOf(matcher.group(3)) : null;
        }
        return null;
    }

    protected Long getEntityCount(String entityType, String userId) {
        WebResource resource = client.resource(entityUri(entityType));
        ClientResponse response = resource.accept(MediaType.APPLICATION_JSON)
                .type(MediaType.APPLICATION_JSON)
                .header(AbstractRestResource.AUTH_HEADER_NAME, userId)
                .head();
        return Long.valueOf(getPaginationTotal(response));
    }

    protected UriBuilder ehriUriBuilder(String... segments) {
        UriBuilder builder = UriBuilder.fromPath(getExtensionEntryPointUri());
        for (String segment : segments) {
            builder = builder.segment(segment);
        }
        return builder;
    }

    protected UriBuilder entityUriBuilder(String entityType, String... segments) {
        List<String> segs = Lists.newArrayList(
                AbstractRestResource.RESOURCE_ENDPOINT_PREFIX,
                entityType);
        segs.addAll(Lists.newArrayList(segments));
        return ehriUriBuilder(segs.toArray(new String[segs.size()]));
    }

    protected URI entityUri(String entityType, String... segments) {
        List<String> segs = Lists.newArrayList(
                AbstractRestResource.RESOURCE_ENDPOINT_PREFIX,
                entityType);
        segs.addAll(Lists.newArrayList(segments));
        return ehriUriBuilder(segs.toArray(new String[segs.size()])).build();
    }

    protected URI ehriUri(String... segments) {
        return ehriUriBuilder(segments).build();
    }

    protected WebResource.Builder jsonCallAs(String user, URI uri) {
        return callAs(user, uri)
                .accept(MediaType.APPLICATION_JSON)
                .type(MediaType.APPLICATION_JSON);
    }

    protected WebResource.Builder jsonCallAs(String user, String... segments) {
        return callAs(user, segments)
                .accept(MediaType.APPLICATION_JSON)
                .type(MediaType.APPLICATION_JSON);
    }

    protected WebResource.Builder callAs(String user, URI uri) {
        return client.resource(uri)
                .header(AbstractRestResource.AUTH_HEADER_NAME, user);
    }

    protected WebResource.Builder callAs(String user, String... segments) {
        return callAs(user, ehriUriBuilder(segments).build());
    }

    protected void assertStatus(ClientResponse.Status status, ClientResponse response) {
        org.junit.Assert.assertEquals(status.getStatusCode(), response.getStatus());
    }

    protected void assertValidJsonData(ClientResponse response) {
        try {
            Bundle.fromString(response.getEntity(String.class));
        } catch (DeserializationError deserializationError) {
            throw new RuntimeException(deserializationError);
        }
    }

    protected String readResourceFileAsString(String resourceName)
            throws java.io.IOException {
        URL url = Resources.getResource(resourceName);
        return Resources.toString(url, Charsets.UTF_8);
    }


    protected final Comparator<Bundle> bundleComparator = new Comparator<Bundle>() {
        @Override
        public int compare(Bundle a, Bundle b) {
            return a.getId().compareTo(b.getId());
        }
    };

    private String getJson(URI uri, String userId, MultivaluedMap<String, String> params) {
        WebResource resource = client.resource(uri).queryParams(params);
        return resource.accept(MediaType.APPLICATION_JSON)
                .type(MediaType.APPLICATION_JSON)
                .header(AbstractRestResource.AUTH_HEADER_NAME, userId)
                .get(String.class);
    }
}