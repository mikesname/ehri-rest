package eu.ehri.project.importers.cvoc;

import eu.ehri.project.importers.ImportLog;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class XmlSkosImporterTest extends AbstractSkosImporterTest {
    @Ignore("XML SKOS importer fails for simple file")
    @Test
    public void testImportFile1() throws Exception {
        SkosImporter importer = new XmlSkosImporter(graph, actioner, vocabulary);
        ImportLog importLog = importer
                .importFile(ClassLoader.getSystemResourceAsStream(FILE1), "simple 1");
        assertEquals(1, importLog.getCreated());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testImportFile2() throws Exception {
        SkosImporter importer = new XmlSkosImporter(graph, actioner, vocabulary);
        importer.setFormat("N3");
        ImportLog importLog = importer
                .importFile(ClassLoader.getSystemResourceAsStream(FILE2), "simple 2");
        assertEquals(1, importLog.getCreated());
    }

    @Ignore("XML SKOS importer fails for repositories file")
    @Test
    public void testImportFile3() throws Exception {
        SkosImporter importer = new XmlSkosImporter(graph, actioner, vocabulary);
        ImportLog importLog1 = importer
                .importFile(ClassLoader.getSystemResourceAsStream(FILE3), "repositories");
        assertEquals(23, importLog1.getCreated());
        ImportLog importLog2 = importer
                .importFile(ClassLoader.getSystemResourceAsStream(FILE3), "repositories");
        assertEquals(23, importLog2.getUnchanged());
    }

    @Test
    public void testImportFile4() throws Exception {
        SkosImporter importer = new XmlSkosImporter(graph, actioner, vocabulary);
        ImportLog importLog1 = importer
                .importFile(ClassLoader.getSystemResourceAsStream(FILE4), "camps");
        assertEquals(8, importLog1.getCreated());
        ImportLog importLog2 = importer
                .importFile(ClassLoader.getSystemResourceAsStream(FILE4), "camps");
        assertEquals(8, importLog2.getUnchanged());
//        printGraph(graph);
    }

    @Test
    public void testImportFile5() throws Exception {
        SkosImporter importer = new XmlSkosImporter(graph, actioner, vocabulary);
        ImportLog importLog1 = importer
                .importFile(ClassLoader.getSystemResourceAsStream(FILE5), "ghettos");
        assertEquals(2, importLog1.getCreated());
        ImportLog importLog2 = importer
                .importFile(ClassLoader.getSystemResourceAsStream(FILE5), "ghettos");
        assertEquals(2, importLog2.getUnchanged());
    }
}
