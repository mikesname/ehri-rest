package eu.ehri.project.commands;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.utils.fixtures.FixtureLoader;
import eu.ehri.project.utils.fixtures.FixtureLoaderFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.io.File;
import java.io.FileInputStream;

/**
 * Import EAD from the command line...
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class LoadFixtures extends BaseCommand implements Command {

    final static String NAME = "load-fixtures";

    /**
     * Constructor.
     * 
     */
    public LoadFixtures() {
    }

    @Override
    protected void setCustomOptions() {
        options.addOption(new Option("init",
                "Initialize graph before loading fixtures"));
    }

    @Override
    public String getHelp() {
        return "Usage: load-fixtures";
    }

    @Override
    public String getUsage() {
        return "Load the fixtures into the database.";
    }

    /**
     * Command-line entry-point (for testing.)
     * 
     * @throws Exception
     */
    @Override
    public int execWithOptions(final FramedGraph<? extends TransactionalGraph> graph,
            CommandLine cmdLine) throws Exception {
        boolean initialize = cmdLine.hasOption("init");
        FixtureLoader loader = FixtureLoaderFactory.getInstance(graph, initialize);
        if (cmdLine.getArgList().size() == 1) {
            String path = cmdLine.getArgs()[0];
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                throw new RuntimeException(String.format(
                        "Fixture file: '%s does not exist or is not a file", path));
            }
            System.err.println("Loading fixture file: " + path);
            FileInputStream inputStream = new FileInputStream(file);
            try {
                loader.loadTestData(inputStream);
            } finally {
                inputStream.close();
            }
        } else {
            // Load default fixtures...
            loader.loadTestData();
        }

        return 0;
    }
}
