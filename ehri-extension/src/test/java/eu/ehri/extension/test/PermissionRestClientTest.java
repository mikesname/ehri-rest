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
import com.google.common.collect.ImmutableList;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import eu.ehri.project.acl.ContentTypes;
import eu.ehri.project.acl.PermissionType;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.persistence.Bundle;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.sun.jersey.api.client.ClientResponse.Status.BAD_REQUEST;
import static com.sun.jersey.api.client.ClientResponse.Status.CREATED;
import static com.sun.jersey.api.client.ClientResponse.Status.OK;
import static com.sun.jersey.api.client.ClientResponse.Status.UNAUTHORIZED;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test Permissions resource.
 */
public class PermissionRestClientTest extends BaseRestClientTest {

    static final String LIMITED_USER_NAME = "reto";
    static final String TEST_HOLDER_IDENTIFIER = "r2";

    private String jsonDocumentaryUnitTestStr;

    @Before
    public void setUp() throws Exception {
        jsonDocumentaryUnitTestStr = readResourceFileAsString("documentaryUnit.json");
    }

    @Test
    public void testSettingGlobalPermissionMatrix()
            throws
            UniformInterfaceException, IOException {

        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.PERMISSION, LIMITED_USER_NAME))
                .get(ClientResponse.class);

        assertStatus(OK, response);
        String data = response.getEntity(String.class);
        System.out.println("DATA: " + data);

        List<Map<String, Map<String, List<String>>>> currentMatrix = getInheritedMatrix(data);
        // Check we don't ALREADY have documentaryUnit -> create/delete perms
        assertNull(currentMatrix.get(0).get(LIMITED_USER_NAME)
                .get(ContentTypes.DOCUMENTARY_UNIT.getName()));
        assertNull(currentMatrix.get(0).get(LIMITED_USER_NAME)
                .get(ContentTypes.DOCUMENTARY_UNIT.getName()));

        // Set the permission via REST
        response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.PERMISSION, LIMITED_USER_NAME))
                .entity(jsonMapper.writeValueAsBytes(getTestMatrix()))
                .post(ClientResponse.class);

        assertStatus(OK, response);

        response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.PERMISSION, LIMITED_USER_NAME))
                .get(ClientResponse.class);

        assertStatus(OK, response);
        data = response.getEntity(String.class);
        List<Map<String, Map<String, List<String>>>> newMatrix = getInheritedMatrix(data);

        // Check we don't ALREADY have documentaryUnit -> create/delete perms
        assertTrue(newMatrix.get(0).get(LIMITED_USER_NAME)
                .get(ContentTypes.DOCUMENTARY_UNIT.getName())
                .contains(PermissionType.CREATE.getName()));
        assertTrue(newMatrix.get(0).get(LIMITED_USER_NAME)
                .get(ContentTypes.DOCUMENTARY_UNIT.getName())
                .contains(PermissionType.DELETE.getName()));
    }

    @Test
    public void testPermissionSetPermissionDenied() throws Exception {

        // Test a user setting his own permissions over REST - this should
        // obviously fail...
        byte[] bytes = jsonMapper.writeValueAsBytes(getTestMatrix());
        ClientResponse response = jsonCallAs(LIMITED_USER_NAME,
                ehriUri(Entities.PERMISSION, LIMITED_USER_NAME))
                .post(ClientResponse.class, bytes);
        assertStatus(UNAUTHORIZED, response);
        // TODO: Figure out why no content ever seems to be returned here?
    }

    @Test
    public void testGivingBadPermsErrorsCorrectly() throws Exception {

        // If we give a permission matrix for a content type that doesn't
        // exist we should get a DeserializationError in return.
        Map<String, List<String>> testMatrix = getTestMatrix();
        testMatrix.put(
                "IDONTEXIST",
                new ImmutableList.Builder<String>().add(
                        PermissionType.CREATE.getName()).build());

        // Set the permission via REST
        byte[] bytes = jsonMapper.writeValueAsBytes(testMatrix);
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.PERMISSION, LIMITED_USER_NAME))
                .post(ClientResponse.class, bytes);
        assertStatus(BAD_REQUEST, response);
    }

    @Test
    public void testSettingGlobalPermissions() throws Exception {

        ClientResponse response = jsonCallAs(LIMITED_USER_NAME,
                getCreationUri()).entity(jsonDocumentaryUnitTestStr)
                .post(ClientResponse.class);
        assertStatus(UNAUTHORIZED, response);

        // Set the permission via REST
        byte[] bytes = jsonMapper.writeValueAsBytes(getTestMatrix());
        response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.PERMISSION, LIMITED_USER_NAME))
                .post(ClientResponse.class, bytes);

        assertStatus(OK, response);

        // Retry the create action
        response = jsonCallAs(LIMITED_USER_NAME, getCreationUri())
                .entity(jsonDocumentaryUnitTestStr)
                .post(ClientResponse.class);

        // Should get CREATED this time...
        assertStatus(CREATED, response);

        // Finally, delete the item
        response = jsonCallAs(LIMITED_USER_NAME,
                response.getLocation()).delete(ClientResponse.class);

        // Should get CREATED this time...
        assertStatus(OK, response);
    }

    @Test
    public void testSettingScopedPermissions() throws Exception {
        // Grant permissions for a user to create items within this scope.

        String r2 = "r2";
        String r3 = "r3";

        // The user shouldn't be able to create docs with r2
        ClientResponse response = jsonCallAs(LIMITED_USER_NAME,
                getCreationUriFor(r2)).entity(jsonDocumentaryUnitTestStr)
                .post(ClientResponse.class);
        assertStatus(UNAUTHORIZED, response);

        // Or r3...
        response = jsonCallAs(LIMITED_USER_NAME, getCreationUriFor(r3))
                .entity(jsonDocumentaryUnitTestStr)
                .post(ClientResponse.class);
        assertStatus(UNAUTHORIZED, response);

        // Now grant the user permissions to create just within
        // the scope of r2
        String permData = "{\"documentaryUnit\": [\"create\"]}";

        URI grantUri = ehriUri(Entities.PERMISSION, LIMITED_USER_NAME, "scope", r2);

        response = jsonCallAs(getAdminUserProfileId(), grantUri)
                .entity(permData)
                .post(ClientResponse.class);
        System.out.println(response.getEntity(String.class));
        assertStatus(OK, response);

        // Now creation should succeed...
        response = jsonCallAs(LIMITED_USER_NAME, getCreationUriFor(r2))
                .entity(jsonDocumentaryUnitTestStr)
                .post(ClientResponse.class);
        assertStatus(CREATED, response);

        // But r3 should still fail...
        // Or r3...
        response = jsonCallAs(LIMITED_USER_NAME, getCreationUriFor(r3))
                .entity(jsonDocumentaryUnitTestStr)
                .post(ClientResponse.class);
        assertStatus(UNAUTHORIZED, response);

        // And the user himself should not be able to grant
        // others the ability to create within that scope.
        String otherUserName = "linda";
        String grantPermData = "{\"documentaryUnit\": [\"grant\"]}";
        URI otherGrantUri = ehriUri(Entities.PERMISSION, otherUserName, "scope", r2);

        response = jsonCallAs(LIMITED_USER_NAME, otherGrantUri).entity(grantPermData)
                .post(ClientResponse.class);

        assertStatus(UNAUTHORIZED, response);

    }

    @Test
    public void testSettingItemPermissions() throws Exception {

        // Fetch an existing item's data
        String targetResourceId = "c4";
        URI targetResourceUri = ehriUri(Entities.DOCUMENTARY_UNIT, targetResourceId);

        ClientResponse response = jsonCallAs(LIMITED_USER_NAME, targetResourceUri)
                .get(ClientResponse.class);
        assertStatus(OK, response);

        // First try and update the item
        String testUpdateString = Bundle
                .fromString(response.getEntity(String.class))
                .withDataValue("testKey", "testValue").toJson();

        response = jsonCallAs(LIMITED_USER_NAME, targetResourceUri)
                .entity(testUpdateString)
                .put(ClientResponse.class);
        assertStatus(UNAUTHORIZED, response);

        // Now grant the user permissions to update and delete just on this item
        String permData = "[\"update\", \"delete\"]";

        // Set the permission via REST
        response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.PERMISSION,
                        LIMITED_USER_NAME, targetResourceId))
                .entity(permData)
                .post(ClientResponse.class);

        assertStatus(OK, response);

        // Retry the create action
        response = jsonCallAs(LIMITED_USER_NAME, targetResourceUri).entity(testUpdateString)
                .put(ClientResponse.class);
        // Should get UPDATED this time...
        assertStatus(OK, response);

        // Finally, delete the item
        response = jsonCallAs(LIMITED_USER_NAME, targetResourceUri)
                .delete(ClientResponse.class);

        // Should get CREATED this time...
        assertStatus(OK, response);
    }

    private List<Map<String, Map<String, List<String>>>> getInheritedMatrix(String json) throws IOException {
        TypeReference<LinkedList<HashMap<String, Map<String, List<String>>>>> typeRef = new TypeReference<LinkedList<HashMap<String, Map<String, List<String>>>>>() {
        };
        return jsonMapper.readValue(json, typeRef);
    }

    @SuppressWarnings("serial")
    private Map<String, List<String>> getTestMatrix() {
        // @formatter:off
        return new HashMap<String, List<String>>() {{
            put(ContentTypes.DOCUMENTARY_UNIT.getName(), new LinkedList<String>() {{
                add(PermissionType.CREATE.getName());
                add(PermissionType.DELETE.getName());
                add(PermissionType.UPDATE.getName());
            }});
            put(ContentTypes.REPOSITORY.getName(), new LinkedList<String>() {{
                add(PermissionType.CREATE.getName());
                add(PermissionType.DELETE.getName());
                add(PermissionType.UPDATE.getName());
            }});
        }};
    }

    private URI getCreationUri() {
        return ehriUri(Entities.REPOSITORY, TEST_HOLDER_IDENTIFIER, Entities.DOCUMENTARY_UNIT);
    }

    private URI getCreationUriFor(String id) {
        return ehriUri(Entities.REPOSITORY, id, Entities.DOCUMENTARY_UNIT);
    }
}
