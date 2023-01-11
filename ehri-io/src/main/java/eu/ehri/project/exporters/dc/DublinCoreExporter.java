package eu.ehri.project.exporters.dc;

import eu.ehri.project.exporters.xml.XmlExporter;
import eu.ehri.project.models.base.Described;
import eu.ehri.project.models.base.Entity;
import eu.ehri.project.models.base.Identifiable;
import org.w3c.dom.Document;

import java.io.IOException;
import java.io.OutputStream;

public interface DublinCoreExporter<T extends Identifiable> extends XmlExporter<T> {
    /**
     * Export an item as a DC document.
     *
     * @param item         the item
     * @param outputStream the output stream to write to.
     * @param langCode     the preferred language code when multiple
     *                     descriptions are available
     */
    void export(T item, OutputStream outputStream, String langCode) throws IOException;

    /**
     * Export an item as a DC document.
     *
     * @param item     the item
     * @param langCode the preferred language code when multiple
     *                 descriptions are available
     * @return a DOM document
     */
    Document export(T item, String langCode) throws IOException;
}
