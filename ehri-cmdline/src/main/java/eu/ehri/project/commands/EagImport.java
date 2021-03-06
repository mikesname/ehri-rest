package eu.ehri.project.commands;

import eu.ehri.project.importers.EagHandler;
import eu.ehri.project.importers.EagImporter;

/**
 * @author Linda Reijnhoudt (https://github.com/lindareijnhoudt)
 */
public class EagImport  extends ImportCommand implements Command {

    final static String NAME = "eag-import";

    /**
     * Constructor.
     */
    public EagImport() {
        super(EagHandler.class, EagImporter.class);
    }
    
    @Override
    public String getHelp() {
        return "Usage: " + NAME + " [OPTIONS] -user <user-id> -scope <agent-id> <neo4j-graph-dir> <eag1.xml> <eag2.xml> ... <eagN.xml>";
    }

    @Override
    public String getUsage() {
        String sep = System.getProperty("line.separator");
        String help = "Import an EAG file into the graph database, using the specified"
                + sep + "Repository and User.";
        return help;
    }

   
   
}
