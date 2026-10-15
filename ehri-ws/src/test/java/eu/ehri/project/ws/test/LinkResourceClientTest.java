/*
 * Copyright 2026 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen), King's College London,
 * Georg-August-Universitaet Goettingen Stiftung Oeffentlichen Rechts,
 * NIOD Institute for War, Holocaust and Genocide Studies (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen).
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.ehri.project.ws.test;

import com.sun.jersey.api.client.ClientResponse;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.ws.GenericResource;
import eu.ehri.project.ws.LinkResource;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.ws.base.AbstractResource;
import org.junit.Test;

import java.util.List;

import static com.sun.jersey.api.client.ClientResponse.Status.CREATED;
import static com.sun.jersey.api.client.ClientResponse.Status.GONE;
import static com.sun.jersey.api.client.ClientResponse.Status.NO_CONTENT;
import static com.sun.jersey.api.client.ClientResponse.Status.NOT_FOUND;
import static com.sun.jersey.api.client.ClientResponse.Status.OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LinkResourceClientTest extends AbstractResourceClientTest {

    private static final String jsonLinkTestString = "{\"type\": \"Link\", \"data\":{\"identifier\": \"39dj28dhs\", " +
            "\"body\": \"test\", \"type\": \"associative\"}}";

    @Test
    public void testCreateLink() throws Exception {
        // Create a link annotation between two objects
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                entityUriBuilder(Entities.LINK)
                        .queryParam(LinkResource.TARGET_PARAM, "c1")
                        .queryParam(LinkResource.SOURCE_PARAM, "c4").build())
                .entity(jsonLinkTestString)
                .post(ClientResponse.class);
        assertStatus(CREATED, response);
    }

    @Test
    public void testCreateLinkInContext() throws Exception {
        Bundle link = createLinkInContext("vc1");
        assertEquals("vc1", link.getRelations(Ontology.LINK_HAS_CONTEXT).get(0).getId());

        List<Bundle> contextLinks = getItemList(
                entityUri(Entities.VIRTUAL_UNIT, "vc1", "contextlinks"), getAdminUserProfileId());
        assertEquals(1, contextLinks.size());
        assertEquals(link.getId(), contextLinks.get(0).getId());

        List<Bundle> itemLinks = getItemList(
                ehriUriBuilder(GenericResource.ENDPOINT, "c1", "links")
                        .queryParam(LinkResource.CONTEXT_PARAM, "vc1").build(),
                getAdminUserProfileId());
        assertEquals(1, itemLinks.size());
        assertEquals(link.getId(), itemLinks.get(0).getId());
    }

    @Test
    public void testCreateLinkInBadContext() throws Exception {
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                entityUriBuilder(Entities.LINK)
                        .queryParam(LinkResource.TARGET_PARAM, "c1")
                        .queryParam(LinkResource.SOURCE_PARAM, "c4")
                        .queryParam(LinkResource.CONTEXT_PARAM, "c2").build())
                .entity(jsonLinkTestString)
                .post(ClientResponse.class);
        assertStatus(NOT_FOUND, response);
    }

    @Test
    public void testSetAndRemoveLinkContext() throws Exception {
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                entityUriBuilder(Entities.LINK, "link1", "context")
                        .queryParam(AbstractResource.ID_PARAM, "vc1").build())
                .put(ClientResponse.class);
        assertStatus(OK, response);
        Bundle link = Bundle.fromString(response.getEntity(String.class));
        assertEquals("vc1", link.getRelations(Ontology.LINK_HAS_CONTEXT).get(0).getId());

        response = jsonCallAs(getAdminUserProfileId(),
                entityUri(Entities.LINK, "link1", "context"))
                .delete(ClientResponse.class);
        assertStatus(OK, response);
        link = Bundle.fromString(response.getEntity(String.class));
        assertTrue(link.getRelations(Ontology.LINK_HAS_CONTEXT).isEmpty());
    }

    @Test
    public void testDeleteVirtualUnitDeletesContextualLinks() throws Exception {
        Bundle link = createLinkInContext("vu3");
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                entityUri(Entities.VIRTUAL_UNIT, "vu3"))
                .delete(ClientResponse.class);
        assertStatus(NO_CONTENT, response);
        response = jsonCallAs(getAdminUserProfileId(),
                entityUri(Entities.LINK, link.getId()))
                .get(ClientResponse.class);
        // Gone, rather than not found, since the link was versioned on deletion
        assertStatus(GONE, response);
    }

    private Bundle createLinkInContext(String context) throws Exception {
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                entityUriBuilder(Entities.LINK)
                        .queryParam(LinkResource.TARGET_PARAM, "c1")
                        .queryParam(LinkResource.SOURCE_PARAM, "a1")
                        .queryParam(LinkResource.CONTEXT_PARAM, context).build())
                .entity(jsonLinkTestString)
                .post(ClientResponse.class);
        assertStatus(CREATED, response);
        return Bundle.fromString(response.getEntity(String.class));
    }
}
