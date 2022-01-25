package eu.ehri.project.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import eu.ehri.project.test.AbstractFixtureTest;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

public class GraphQLImplTest extends AbstractFixtureTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testWithUnprivilegedUser() throws Exception {
        GraphQLImpl graphQL = new GraphQLImpl(anonApi());
        GraphQLSchema schema = graphQL.getSchema();
        String testQuery = readResourceFileAsString("testquery.graphql");
        ExecutionResult result = GraphQL.newGraphQL(schema).build().execute(testQuery);

        // System.out.println(result);
        assertTrue(result.getErrors().isEmpty());
        JsonNode data = mapper.valueToTree(result.toSpecification());
        assertTrue(data.path("data").path("c1").isNull());
    }

    @Test
    public void testWithPrivilegedUser() throws Exception {
        GraphQLImpl graphQL = new GraphQLImpl(api(validUser));
        GraphQLSchema schema = graphQL.getSchema();
        String testQuery = readResourceFileAsString("testquery.graphql");
        ExecutionResult result = GraphQL.newGraphQL(schema).build().execute(testQuery);

        // System.out.println(result);
        assertTrue(result.getErrors().isEmpty());
        JsonNode data = mapper.valueToTree(result.toSpecification());
        System.out.println(data.toPrettyString());

        assertEquals("c1", data.path("data").path("c1").path("id").textValue());
        assertEquals(0, data.path("data").path("c1").path("ancestors").size());
        assertEquals(1, data.path("data").path("c1")
                .path("children").path("items").size());
        assertEquals(2, data.path("data").path("c1")
                .path("allChildren").path("items").size());
        assertFalse(data.path("data").path("c1").path("itemCount").isMissingNode());
        assertEquals(1, data.path("data").path("c1").path("itemCount").intValue());
        assertEquals(0, data.path("data").path("c4").path("itemCount").intValue());
        assertEquals("c2", data.path("data").path("c1")
                .path("children").path("items").path(0)
                .path("id").textValue());
        assertEquals("c3", data.path("data").path("c1")
                .path("children").path("items").path(0)
                .path("children").path("items").path(0)
                .path("id").textValue());
        assertEquals("a2", data.path("data").path("c3").path("related")
                .path(0).path("item").path("id").textValue());
        assertEquals("Amsterdam", data.path("data").path("c1").path("repository")
                .path("english").path("addresses").path(0)
                .path("municipality").textValue());
        assertEquals("test@example.com", data.path("data").path("c1")
                .path("repository").path("english").path("addresses").path(0)
                .path("email").path(0).textValue());
        assertEquals(2, data.path("data").path("c3").path("ancestors").size());
        assertEquals("c2", data.path("data").path("c3").path("ancestors")
                .path(0).path("id").textValue());
        assertEquals("c1", data.path("data").path("c3").path("ancestors")
                .path(1).path("id").textValue());
        assertEquals("cvocc1", data.path("data").path("cvocc1")
                .path("identifier").textValue());
        assertEquals(51.0, data.path("data").path("cvocc1")
                .path("latitude").numberValue());
        assertEquals(0.0, data.path("data").path("cvocc1")
                .path("longitude").numberValue());
        assertEquals("ann7", data.path("data").path("c4")
                .path("annotations").path(0).path("id").textValue());
        assertEquals("scopeAndContent", data.path("data").path("c3")
                .path("annotations").path(0).path("field").textValue());
        assertEquals("Mike", data.path("data").path("c3")
                .path("annotations").path(0).path("by").textValue());
        assertFalse(data.path("data").path("topLevelOnly")
                .path("items").path(0).path("id").isMissingNode());
        assertEquals(3, data.path("data").path("topLevelOnly")
                .path("items").size());
        assertEquals(5, data.path("data").path("allLevels")
                .path("items").size());
        assertFalse(data.path("data").path("topLevelDocumentaryUnits")
                .path("items").path(0).path("id").isMissingNode());
        assertFalse(data.path("data").path("wrongType").isMissingNode());
        assertTrue(data.path("data").path("wrongType").isNull());

    }
}