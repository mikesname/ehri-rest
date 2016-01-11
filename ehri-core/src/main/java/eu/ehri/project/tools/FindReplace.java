/*
 * Copyright 2015 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen), King's College London,
 * Georg-August-Universitaet Goettingen Stiftung Oeffentlichen Rechts
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.ehri.project.tools;

import com.tinkerpop.blueprints.CloseableIterable;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.annotations.EntityType;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A grab-bag of useful functions for making changes to the
 * graph data.
 * <p>
 * NB: Most uses of the methods here can be trivially accomplished
 * with mutating Cypher but that's an even sharper tool. The purpose
 * here is to formalise some very common operations with specific
 * checks in place.
 */
public class FindReplace {
    private final GraphManager manager;

    public FindReplace(FramedGraph<?> graph) {
        this.manager = GraphManagerFactory.getInstance(graph);
    }

    /**
     * Replace an existing property value with another value across an
     * entire class of items.
     *
     * @param entityClass   the item class
     * @param propertyName  the property name
     * @param existingValue the existing value
     * @param newValue      the new value
     * @return the number of items changed
     */
    public long propertyValue(EntityClass entityClass, String propertyName,
            String existingValue, String newValue) {
        checkLegalPropertyName(propertyName);
        try (CloseableIterable<Vertex> vertices = manager.getVertices(entityClass)) {
            long changes = 0L;
            for (Vertex v : vertices) {
                Object current = v.getProperty(propertyName);
                if (current instanceof List) {
                    List<String> stringList = v.getProperty(propertyName);
                    int valuesChanged = replaceInList(stringList, existingValue, newValue);
                    if (valuesChanged > 0) {
                        changes += valuesChanged;
                        manager.setProperty(v, propertyName, stringList.toArray(new String[stringList.size()]));
                    }
                } else if (current instanceof String) {
                    if (existingValue.equals(current)) {
                        changes++;
                        manager.setProperty(v, propertyName, newValue);
                    }
                }
            }
            return changes;
        }
    }

    /**
     * Replace a substring of a property value across an entire
     * item class. Property values must either be strings of
     * string arrays.
     *
     * @param entityClass  the item class
     * @param propertyName the property name
     * @param pattern      the existing value
     * @param replacement  the new value
     * @return the number of properties changed
     */
    public long propertyValueRE(EntityClass entityClass, String propertyName,
            Pattern pattern, String replacement) {
        checkLegalPropertyName(propertyName);
        try (CloseableIterable<Vertex> vertices = manager.getVertices(entityClass)) {
            long changes = 0L;
            for (Vertex v : vertices) {
                Object current = v.getProperty(propertyName);
                if (current instanceof List) {
                    List<String> stringList = v.getProperty(propertyName);
                    int valuesChanged = regexReplaceInList(stringList, pattern, replacement);
                    if (valuesChanged > 0) {
                        changes += valuesChanged;
                        manager.setProperty(v, propertyName, stringList.toArray(new String[stringList.size()]));
                    }
                } else if (current instanceof String) {
                    Matcher matcher = pattern.matcher((String) current);
                    if (matcher.find()) {
                        changes++;
                        manager.setProperty(v, propertyName, matcher.replaceAll(replacement));
                    }
                }
            }
            return changes;
        }
    }

    /**
     * Rename a property key across an entire class of items.
     *
     * @param entityClass     the item class
     * @param propertyName    the existing property name
     * @param newPropertyName the new property name
     * @return the number of items changed
     */
    public long propertyName(EntityClass entityClass, String propertyName, String newPropertyName) {
        checkLegalPropertyName(propertyName);
        try (CloseableIterable<Vertex> vertices = manager.getVertices(entityClass)) {
            long changes = 0L;
            for (Vertex v : vertices) {
                Set<String> propertyKeys = v.getPropertyKeys();
                if (propertyKeys.contains(propertyName)) {
                    Object current = v.getProperty(propertyName);
                    manager.setProperty(v, newPropertyName, current);
                    manager.setProperty(v, propertyName, null);
                    changes++;
                }
            }
            return changes;
        }
    }

    // Helpers

    private void checkLegalPropertyName(String propertyName) {
        if (propertyName == null
                || propertyName.trim().isEmpty()
                || propertyName.equals(EntityType.ID_KEY)
                || propertyName.equals(EntityType.TYPE_KEY)) {
            throw new IllegalArgumentException("Invalid property name: " + propertyName);
        }
    }

    /**
     * Replaces items in the input list that equal the given existing value
     * with the replacement string, returning the number of changes made.
     *
     * @param listInParam an input list that will be changed in place
     * @param existing    an existing value
     * @param replacement a replacement string
     * @return the number of changes made in the list
     */
    private int replaceInList(List<String> listInParam, String existing, String replacement) {
        int changes = 0;
        for (int i = 0; i < listInParam.size(); i++) {
            String itemAt = listInParam.get(i);
            if (itemAt.equals(existing)) {
                changes++;
                listInParam.set(i, replacement);
            }
        }
        return changes;
    }

    /**
     * Replaces items in the input list that match the given pattern with the replacement string,
     * returning the number of changes made.
     *
     * @param listInParam an input list that will be changed in place
     * @param pattern     a regular expression pattern
     * @param replacement a replacement string
     * @return the number of changes made in the list
     */
    private int regexReplaceInList(List<String> listInParam, Pattern pattern, String replacement) {
        int changes = 0;
        for (int i = 0; i < listInParam.size(); i++) {
            String itemAt = listInParam.get(i);
            Matcher matcher = pattern.matcher(itemAt);
            if (matcher.find()) {
                changes++;
                listInParam.set(i, matcher.replaceAll(replacement));
            }
        }
        return changes;
    }
}