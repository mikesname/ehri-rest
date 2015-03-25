/*
 * Copyright 2015 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie Van Wetenschappen), King's College London,
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

package eu.ehri.project.models;

import com.google.common.collect.Iterables;
import eu.ehri.project.acl.PermissionType;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.test.AbstractFixtureTest;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PermissionGrantTest extends AbstractFixtureTest {

    private PermissionGrant groupGrant;
    private PermissionGrant userGrant;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        groupGrant = manager.getFrame("portalAnnotationGrant", PermissionGrant.class);
        userGrant = manager.getFrame("retoKclWriteGrant", PermissionGrant.class);
    }

    @Test
    public void testGetSubject() throws Exception {
        assertEquals(manager.getFrame("portal", Group.class),
                groupGrant.getSubject());

    }

    @Test
    public void testGetGrantee() throws Exception {
        assertEquals(manager.getFrame("mike", UserProfile.class),
                userGrant.getGrantee());
    }

    @Test
    public void testGetTargets() throws Exception {
        assertTrue(Iterables.contains(groupGrant.getTargets(),
                manager.getFrame(Entities.DOCUMENTARY_UNIT, ContentType.class)));
    }

    @Test
    public void testAddTarget() throws Exception {
        ContentType ct = manager.getFrame(Entities.USER_PROFILE, ContentType.class);
        groupGrant.addTarget(ct);
        assertTrue(Iterables.contains(groupGrant.getTargets(), ct));
    }

    @Test
    public void testGetPermission() throws Exception {
        assertEquals(manager.getFrame(PermissionType.ANNOTATE.getName(),
                Permission.class), groupGrant.getPermission());
    }

    @Test
    public void testSetPermission() throws Exception {
        Permission perm = manager.getFrame(PermissionType.CREATE.getName(), Permission.class);
        groupGrant.setPermission(perm);
        assertEquals(perm, groupGrant.getPermission());

    }

    @Test
    public void testGetScope() throws Exception {
        Repository r1 = manager.getFrame("r1", Repository.class);
        assertEquals(r1, userGrant.getScope());
    }

    @Test
    public void testSetScope() throws Exception {
        DocumentaryUnit c1 = manager.getFrame("c1", DocumentaryUnit.class);
        userGrant.setScope(c1);
        assertEquals(c1, userGrant.getScope());
    }

    @Test
    public void testRemoveScope() throws Exception {
        Repository r1 = manager.getFrame("r1", Repository.class);
        userGrant.removeScope(r1);
        assertNull(userGrant.getScope());
    }
}