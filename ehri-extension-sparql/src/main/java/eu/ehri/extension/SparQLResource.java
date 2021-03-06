package eu.ehri.extension;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tinkerpop.blueprints.oupls.sail.pg.PropertyGraphSail;
import info.aduna.iteration.CloseableIteration;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.graphdb.GraphDatabaseService;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.impl.EmptyBindingSet;
import org.openrdf.query.parser.ParsedQuery;
import org.openrdf.query.parser.QueryParser;
import org.openrdf.query.parser.sparql.SPARQLParser;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Resource for executing SparQL queries on the graph
 * database.
 * <p/>
 * * Insecure and SHOULD NOT be a public endpoint.
 * <p/>
 * Example query:
 * <p/>
 * <pre>
 *     <code>
 * PREFIX edge:   <http://tinkerpop.com/pgm/edge/>
 * PREFIX vertex: <http://tinkerpop.com/pgm/vertex/>
 * PREFIX prop:   <http://tinkerpop.com/pgm/property/>
 * PREFIX pgm:    <http://tinkerpop.com/pgm/ontology#>
 * PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
 *
 * # Select all the userProfile nodes and their name properties...
 * SELECT ?n ?u WHERE {
 *    ?u a pgm:Vertex ;
 *       prop:__ISA__  "userProfile" ;
 *       prop:name     ?n .
 * }
 *
 * LIMIT 100
 *     </code>
 * </pre>
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Path("sparql")
public class SparQLResource extends AbstractRestResource {

    public static final String HTTP_EHRI_PROJECT_EU = "http://ehri-project.eu";
    public static final String QUERY_PARAM = "q";

    private static final QueryParser parser = new SPARQLParser();
    private static final JsonFactory factory = new JsonFactory();
    private static final ObjectMapper mapper = new ObjectMapper(factory);
    private PropertyGraphSail sail;

    /**
     * @param database Injected neo4j database
     */
    public SparQLResource(@Context GraphDatabaseService database) {
        super(database);

    }

    /**
     * Run a sparql query.
     *
     * @param q A valid SparQL query string
     * @return A JSON-formatted response
     * @throws Exception
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public Response sparqlQuery(
            @DefaultValue("") @QueryParam(QUERY_PARAM) String q) throws Exception {

        initSail();
        ParsedQuery query = parser.parseQuery(q, HTTP_EHRI_PROJECT_EU);
        CloseableIteration<? extends BindingSet, QueryEvaluationException> results
                = sail.getConnection().evaluate(
                query.getTupleExpr(), query.getDataset(), new EmptyBindingSet(), false);
        try {
            List<Map<String, String>> out = Lists.newArrayList();
            while (results.hasNext()) {
                BindingSet next = results.next();
                Map<String, String> set = Maps.newHashMap();
                for (String name : next.getBindingNames()) {
                    set.put(name, next.getValue(name).stringValue());
                }
                out.add(set);
            }
            return Response.ok(mapper.writeValueAsBytes(out)).build();
        } finally {
            results.close();
        }
    }

    /**
     * Initialise the PropertySailGraph
     */
    private void initSail() throws Exception {
        if (sail == null) {
            sail = new PropertyGraphSail(graph.getBaseGraph());
            sail.initialize();
        }
    }
}
