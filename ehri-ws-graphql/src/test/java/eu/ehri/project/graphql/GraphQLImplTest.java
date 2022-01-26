package eu.ehri.project.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.persistence.Serializer;
import eu.ehri.project.test.AbstractFixtureTest;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

public class GraphQLImplTest extends AbstractFixtureTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Make a system event...
        Serializer serializer = new Serializer(graph).withDependentOnly(true);
        Bundle bundle = serializer.entityToBundle(item).withDataValue("test", "foo");
        api(validUser).enableLogging(true).update(bundle, DocumentaryUnit.class, Optional.of("Test message"));
    }

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
        System.out.println(result.getErrors());
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
        assertEquals("Test message", data.path("data").path("c1")
                .path("systemEvents").path(0).path("logMessage").textValue());
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
        assertEquals("nl", data.path("data").path("Country")
                .path("identifier").textValue());
        assertEquals("Netherlands", data.path("data").path("Country")
                .path("name").textValue());
        assertEquals("auths", data.path("data").path("AuthoritativeSet")
                .path("identifier").textValue());
        assertEquals("Authorities", data.path("data").path("AuthoritativeSet")
                .path("name").textValue());
        assertEquals("cvoc1", data.path("data").path("CvocVocabulary")
                .path("identifier").textValue());
        assertEquals("Vocabulary 1", data.path("data").path("CvocVocabulary")
                .path("name").textValue());
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
        assertEquals("Subject Access 1", data.path("data").path("links")
                .path("items").path(1).path("body").path(0).path("name").textValue());
        assertFalse(data.path("data").path("wrongType").isMissingNode());
        assertTrue(data.path("data").path("wrongType").isNull());

    }
}