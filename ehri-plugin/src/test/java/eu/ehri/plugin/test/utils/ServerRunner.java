package eu.ehri.plugin.test.utils;

import java.io.File;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.AbstractGraphDatabase;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.server.NeoServer;
import org.neo4j.server.WrappingNeoServerBootstrapper;
import org.neo4j.server.configuration.ServerConfigurator;

import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;

import eu.ehri.project.test.utils.FixtureLoader;

/**
 * Class that handles running a test Neo4j server.
 * 
 */
public class ServerRunner {

    protected AbstractGraphDatabase graphDatabase;
    protected WrappingNeoServerBootstrapper bootstrapper;
    protected FixtureLoader loader;
    protected NeoServer neoServer;
    protected ServerConfigurator config;

    /**
     * Initialise a new Neo4j Server with the given db name and port.
     * 
     * @param dbName
     * @param dbPort
     */
    public ServerRunner(String dbName, Integer dbPort) {
        // TODO: Work out a better way to configure the path
        final String dbPath = "target/tmpdb_" + dbName;
        graphDatabase = new EmbeddedGraphDatabase(dbPath);

        // Initialize the fixture loader
        loader = new FixtureLoader(new FramedGraph<Neo4jGraph>(new Neo4jGraph(
                graphDatabase)));
        loader.loadTestData();
        // Server configuration. TODO: Work out how to disable server startup
        // and load logging so the test output isn't so noisy...
        config = new ServerConfigurator(graphDatabase);
        config.configuration().setProperty("org.neo4j.server.webserver.port",
                dbPort.toString());
        
        bootstrapper = new WrappingNeoServerBootstrapper(graphDatabase, config);

        // Attempt to ensure database is erased from the disk when
        // the runtime shuts down. This improves repeatability, because
        // if it is still there it'll be appended to on the next run.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                deleteFolder(new File(dbPath));
            }
        });
    }

    /**
     * Get the configurator for the test db. This allows adjusting config before
     * starting it up.
     * 
     * @return
     */
    public ServerConfigurator getConfigurator() {
        return config;
    }

    /**
     * Initialise a new graph database in a given location. This should be
     * unique for each superclass, because otherwise problems can be encountered
     * when another test suite starts up whilst a database is in the process of
     * shutting down.
     * 
     */
    public void start() {
        bootstrapper.start();
    }
    
    public void setUp() {
        loader.loadTestData();
    }
    
    public void tearDown() {
        resetGraph();
    }

    /**
     * Stop the server
     */
    public void stop() {
        bootstrapper.stop();
    }
    
    /**
     * Function for deleting all nodes in a database, restoring it
     * to its initial state.
     * 
     */
    protected long resetGraph() {
        Transaction tx = graphDatabase.beginTx();
        long deleted = 0;
        try {
            for (Node node :  graphDatabase.getAllNodes()) {
                if ((long)node.getId() != 0L) {
                    for (Relationship rel : node.getRelationships()) {
                        rel.delete();
                    }
                    node.delete();
                    deleted++;
                }
            }
            tx.success();
            return deleted;
        } finally {
            tx.finish();
        }
    }

    /**
     * Function for deleting an entire database folder. USE WITH CARE!!!
     * 
     * @param folder
     */
    protected void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) { // some JVMs return null for empty dirs
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }
}
