package eu.ehri.extension.test;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import eu.ehri.extension.SystemEventResource;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.views.EventViews;
import org.codehaus.jackson.JsonNode;
import org.junit.Test;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.sun.jersey.api.client.ClientResponse.Status.CREATED;
import static com.sun.jersey.api.client.ClientResponse.Status.OK;
import static eu.ehri.extension.AbstractRestResource.ID_PARAM;
import static eu.ehri.extension.UserProfileResource.FOLLOWING;
import static eu.ehri.extension.UserProfileResource.WATCHING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SystemEventRestClientTest extends BaseRestClientTest {

    static final String COUNTRY_CODE = "nl";

    private String jsonAgentTestString = "{\"type\": \"repository\", \"data\":{\"identifier\": \"jmp\"}}";

    @Test
    public void testListActions() throws Exception {
        // Create a new agent. We're going to test that this creates
        // a corresponding action.

        List<Map<String, Object>> actionsBefore = getEntityList(
                Entities.SYSTEM_EVENT, getAdminUserProfileId());

        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.COUNTRY, COUNTRY_CODE, Entities.REPOSITORY))
                .entity(jsonAgentTestString)
                .post(ClientResponse.class);

        assertStatus(CREATED, response);

        List<Map<String, Object>> actionsAfter = getEntityList(
                Entities.SYSTEM_EVENT, getAdminUserProfileId());
        System.out.println(actionsAfter);

        // Having created a new Repository, we should have at least one Event.
        assertEquals(actionsBefore.size() + 1, actionsAfter.size());
    }

    @Test
    public void testListActionsWithFilter() throws Exception {
        // Create a new agent. We're going to test that this creates
        // a corresponding action.

        jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.COUNTRY, COUNTRY_CODE, Entities.REPOSITORY))
                .entity(jsonAgentTestString)
                .post(ClientResponse.class);

        // Add a good user filter...
        MultivaluedMap<String, String> goodFilters = new MultivaluedMapImpl();
        goodFilters.add(SystemEventResource.USER_PARAM, getAdminUserProfileId());

        // Add a useless filter that should remove all results
        MultivaluedMap<String, String> badFilters = new MultivaluedMapImpl();
        badFilters.add(SystemEventResource.USER_PARAM, "nobody");

        List<Map<String, Object>> goodFiltered = getEntityList(
                Entities.SYSTEM_EVENT, getAdminUserProfileId(), goodFilters);

        List<Map<String, Object>> badFiltered = getEntityList(
                Entities.SYSTEM_EVENT, getAdminUserProfileId(), badFilters);

        assertTrue(goodFiltered.size() > 0);
        assertEquals(0, badFiltered.size());
    }

    @Test
    public void testGetActionsForItem() throws Exception {

        // Create an item
        client.resource(
                ehriUri(Entities.COUNTRY, COUNTRY_CODE, Entities.REPOSITORY));
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.COUNTRY, COUNTRY_CODE, Entities.REPOSITORY))
                .entity(jsonAgentTestString)
                .post(ClientResponse.class);

        // Get the id
        String url = response.getLocation().toString();
        String id = url.substring(url.lastIndexOf("/") + 1);

        response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.SYSTEM_EVENT, "for", id))
                .get(ClientResponse.class);
        assertStatus(OK, response);
    }

    @Test
    public void testGetVersionsForItem() throws Exception {
        // Create an item
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.REPOSITORY, "r1"))
                .entity(jsonAgentTestString)
                .put(ClientResponse.class);
        assertStatus(OK, response);

        response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.SYSTEM_EVENT, "versions", "r1"))
                .get(ClientResponse.class);

        String json = response.getEntity(String.class);
        // Check the response contains a new version
        JsonNode rootNode = jsonMapper.readValue(json, JsonNode.class);
        assertFalse(rootNode.path(0).path(Bundle.DATA_KEY)
                .path(Ontology.VERSION_ENTITY_DATA).isMissingNode());
        assertStatus(OK, response);
    }

    @Test
    public void testPersonalisedEventList() throws Exception {

        // Create an event by updating an item...
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.REPOSITORY, "r1"))
                .entity(jsonAgentTestString)
                .put(ClientResponse.class);
        assertStatus(OK, response);

        // At present, the personalised event stream for the validUser user should
        // contain all the regular events.
        String user = getRegularUserProfileId();
        String personalisedEventUrl = "/" + Entities.SYSTEM_EVENT + "/forUser/" + user;
        List<Map<String, Object>> events = getItemList(personalisedEventUrl, user);
        assertFalse(events.isEmpty());

        // Now start watching item r1
        URI watchUrl = UriBuilder.fromPath(getExtensionEntryPointUri())
                .segment(Entities.USER_PROFILE)
                .segment(user)
                .segment(WATCHING)
                .queryParam(ID_PARAM, "r1").build();

        jsonCallAs(user, watchUrl).post(ClientResponse.class);

        // Now our event list should contain one item...
        events = getItemList(personalisedEventUrl, user);
        assertEquals(1, events.size());

        // Only get events for people we follow, excluding those
        // for items we watch...
        events = getItemList(personalisedEventUrl + "?" + SystemEventResource.SHOW
                + "=" + EventViews.ShowType.followed, user);
        assertEquals(0, events.size());

        // Now follow the other user...
        URI followUrl = UriBuilder.fromPath(getExtensionEntryPointUri())
                .segment(Entities.USER_PROFILE)
                .segment(user)
                .segment(FOLLOWING)
                .queryParam(ID_PARAM, getAdminUserProfileId()).build();
        jsonCallAs(user, followUrl).post(ClientResponse.class);

        // We should get the event again...
        events = getItemList(personalisedEventUrl + "?" + SystemEventResource.SHOW
                + "=" + EventViews.ShowType.followed, user);
        assertEquals(1, events.size());
    }
}
