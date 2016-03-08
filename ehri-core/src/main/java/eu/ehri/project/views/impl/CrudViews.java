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

package eu.ehri.project.views.impl;

import com.google.common.base.Optional;
import com.tinkerpop.frames.FramedGraph;
import eu.ehri.project.acl.AclManager;
import eu.ehri.project.acl.PermissionType;
import eu.ehri.project.acl.SystemScope;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.base.Accessible;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.persistence.BundleManager;
import eu.ehri.project.persistence.Mutation;
import eu.ehri.project.persistence.Serializer;
import eu.ehri.project.views.Crud;
import eu.ehri.project.views.ViewHelper;

public final class CrudViews<E extends Accessible> implements Crud<E> {
    private final FramedGraph<?> graph;
    private final Class<E> cls;
    private final ViewHelper helper;
    private final GraphManager manager;
    private final Serializer serializer;
    private final PermissionScope scope;
    private final AclManager acl;

    /**
     * Scoped Constructor.
     *
     * @param graph The graph
     * @param cls   The entity class to return
     * @param scope The permission scope
     */
    public CrudViews(FramedGraph<?> graph, Class<E> cls,
            PermissionScope scope) {
        this.graph = graph;
        this.cls = cls;
        this.scope = Optional.fromNullable(scope).or(SystemScope.getInstance());
        helper = new ViewHelper(graph, this.scope);
        acl = helper.getAclManager();
        serializer = new Serializer(graph);
        manager = GraphManagerFactory.getInstance(graph);
    }

    /**
     * Constructor.
     *
     * @param graph The graph
     * @param cls   The entity class to return
     */
    public CrudViews(FramedGraph<?> graph, Class<E> cls) {
        this(graph, cls, SystemScope.getInstance());
    }

    /**
     * Fetch an item, as a user. This only provides access control.
     *
     * @param id The item id
     * @param user The current user
     * @return The given framed vertex
     * @throws ItemNotFound
     */
    public E detail(String id, Accessor user) throws ItemNotFound {
        E item = manager.getEntity(id, cls);
        if (!acl.canAccess(item, user)) {
            throw new ItemNotFound(id);
        }
        return item;
    }

    /**
     * Update an object bundle, also updating dependent items.
     *
     * @param bundle     The item's data bundle
     * @param user       The current user
     * @return The updated framed vertex
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws ItemNotFound
     */
    public Mutation<E> update(Bundle bundle, Accessor user)
            throws PermissionDenied, ValidationError, ItemNotFound {
        E entity = graph.frame(manager.getVertex(bundle.getId()), cls);
        helper.checkEntityPermission(entity, user, PermissionType.UPDATE);
        return getPersister(scope).update(bundle, cls);
    }

    /**
     * Create a new object of type `E` from the given data, within the scope of
     * `scope`.
     *
     * @param bundle     The item's data bundle
     * @param user       The current user
     * @return The created framed vertex
     * @throws PermissionDenied
     * @throws ValidationError
     */
    public E create(Bundle bundle, Accessor user)
            throws PermissionDenied, ValidationError {
        helper.checkContentPermission(user, helper.getContentTypeEnum(cls),
                PermissionType.CREATE);
        E item = getPersister(scope).create(bundle, cls);
        // If a user creates an item, grant them OWNER perms on it.
        // Owner permissions do not have a scope.
        // FIXME: Currently a hack here so this doesn't apply to admin
        // users - but it probably should...
        if (!AclManager.belongsToAdmin(user)) {
            acl.withScope(SystemScope.INSTANCE)
                    .grantPermission(item, PermissionType.OWNER, user);
        }
        // If the scope is not the system, set the permission scope
        // of the item too...
        if (!scope.equals(SystemScope.getInstance())) {
            item.setPermissionScope(scope);
        }
        return item;
    }

    /**
     * Create or update a new object of type `E` from the given data, within the
     * scope of `scope`.
     *
     * @param bundle     The item's data bundle
     * @param user       The current user
     * @return The created framed vertex
     * @throws PermissionDenied
     * @throws ValidationError
     */
    public Mutation<E> createOrUpdate(Bundle bundle, Accessor user)
            throws PermissionDenied, ValidationError {
        helper.checkContentPermission(user, helper.getContentTypeEnum(cls),
                PermissionType.CREATE);
        helper.checkContentPermission(user, helper.getContentTypeEnum(cls),
                PermissionType.UPDATE);
        return getPersister(scope).createOrUpdate(bundle, cls);
    }

    /**
     * Delete an object bundle, following dependency cascades, within the scope
     * of item `scope`.
     *
     * @param id The item ID
     * @param user The current user
     * @return The number of vertices deleted.
     * @throws ItemNotFound
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws SerializationError
     */
    public Integer delete(String id, Accessor user) throws PermissionDenied,
            ValidationError, SerializationError, ItemNotFound {
        E item = manager.getEntity(id, cls);
        helper.checkEntityPermission(item, user, PermissionType.DELETE);
        return getPersister(scope).delete(serializer.entityToBundle(item));
    }

    /**
     * Set the permission scope of the view.
     *
     * @param scope A permission scope
     * @return A new view
     */
    public Crud<E> setScope(PermissionScope scope) {
        return new CrudViews<>(graph, cls,
                Optional.fromNullable(scope).or(SystemScope.INSTANCE));
    }

    // Helpers
    private BundleManager getPersister(PermissionScope scope) {
        return new BundleManager(graph, scope.idPath());
    }
}
