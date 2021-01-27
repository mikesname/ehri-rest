/*
 * Copyright 2020 Data Archiving and Networked Services (an institute of
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

package eu.ehri.extension.base;

import eu.ehri.project.exceptions.*;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.utils.Table;

import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Methods for managing resources that have a superior (hierarchical)
 * relationship to another resource.
 */
public interface ParentResource {

    /**
     * List available resources subordinate to this item. Behaviour is the same
     * as the general list method with respect to parameters.
     * <p>
     * Example:
     * <pre>
     *     <code>
     * curl http://localhost:7474/ehri/[RESOURCE]/[ID]/list
     *     </code>
     * </pre>
     *
     * @return a list of serialized item representations
     */
    Response listChildren(String id, boolean all) throws ItemNotFound;

    /**
     * Create a subordinate resource.
     *
     * @param id        the parent resource ID.
     * @param bundle    a resource bundle.
     * @param accessors the users/groups who can access this item.
     * @return A serialized representation of the created resource.
     */
    Response createChild(String id, Bundle bundle, List<String> accessors)
            throws PermissionDenied, ValidationError, DeserializationError, ItemNotFound;

    /**
     * Delete all
     *
     * <p>
     * Example:
     * <pre>
     *     <code>
     * curl -XDELETE -HX-User:admin http://localhost:7474/ehri/[RESOURCE]/[ID]/list
     *     </code>
     * </pre>
     *
     * @param id  the parent resource ID
     * @param all descend into the hierarchy of any child items
     * @return an ordered list of deleted item IDs
     * @throws HierarchyError if an attempt is made to delete child items that have
     *                        children themselves without using the {{all}} parameter.
     */
    Table deleteChildren(String id, boolean all)
            throws PermissionDenied, ValidationError, DeserializationError, ItemNotFound, HierarchyError;
}
