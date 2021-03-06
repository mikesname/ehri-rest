package eu.ehri.project.views;

import eu.ehri.project.acl.AclManager;
import eu.ehri.project.acl.AnonymousAccessor;
import eu.ehri.project.acl.ContentTypes;
import eu.ehri.project.acl.PermissionType;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.models.DatePeriod;
import eu.ehri.project.models.DocumentDescription;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.Group;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.UndeterminedRelationship;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.PermissionGrantTarget;
import eu.ehri.project.persistence.Bundle;
import eu.ehri.project.test.AbstractFixtureTest;
import eu.ehri.project.test.TestData;
import eu.ehri.project.views.impl.CrudViews;
import eu.ehri.project.views.impl.LoggingCrudViews;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CrudViewsTest extends AbstractFixtureTest {

    @Test
    public void testDetail() throws ItemNotFound {
        Crud<DocumentaryUnit> docViews = new CrudViews<DocumentaryUnit>(graph,
                DocumentaryUnit.class);
        DocumentaryUnit unit = docViews.detail(item.getId(), validUser);
        assertEquals(item.asVertex(), unit.asVertex());
    }

    @Test
    public void testUserProfile() throws ItemNotFound {
        CrudViews<UserProfile> userViews = new CrudViews<UserProfile>(graph,
                UserProfile.class);
        UserProfile user = userViews.detail(validUser.getId(), validUser);
        assertEquals(validUser.asVertex(), user.asVertex());
    }

    @Test(expected = ItemNotFound.class)
    public void testDetailAnonymous() throws ItemNotFound {
        CrudViews<DocumentaryUnit> docViews = new CrudViews<DocumentaryUnit>(
                graph, DocumentaryUnit.class);
        docViews.detail(item.getId(), AnonymousAccessor.getInstance());
    }

    @Test(expected = ItemNotFound.class)
    public void testDetailPermissionDenied() throws ItemNotFound {
        CrudViews<DocumentaryUnit> docViews = new CrudViews<DocumentaryUnit>(
                graph, DocumentaryUnit.class);
        docViews.detail(item.getId(), invalidUser);
    }

    @Test
    public void testCreate() throws Exception {
        CrudViews<DocumentaryUnit> docViews = new CrudViews<DocumentaryUnit>(
                graph, DocumentaryUnit.class);
        Bundle bundle = Bundle.fromData(TestData.getTestDocBundle());
        DocumentaryUnit unit = docViews.create(bundle, validUser);
        assertEquals(TestData.TEST_COLLECTION_NAME, unit.asVertex().getProperty("name"));
    }

    @Test(expected = PermissionDenied.class)
    public void testCreateAsUnauthorized() throws Exception {
        CrudViews<DocumentaryUnit> docViews = new CrudViews<DocumentaryUnit>(
                graph, DocumentaryUnit.class);
        Bundle bundle = Bundle.fromData(TestData.getTestDocBundle());
        DocumentaryUnit unit = docViews.create(bundle, invalidUser);
        assertEquals(TestData.TEST_COLLECTION_NAME, unit.asVertex().getProperty("name"));
    }

    @Test
    public void testCreateAsUnauthorizedAndThenGrant() throws Exception {
        CrudViews<DocumentaryUnit> docViews = new CrudViews<DocumentaryUnit>(
                graph, DocumentaryUnit.class);
        Bundle bundle = Bundle.fromData(TestData.getTestDocBundle());

        try {
            docViews.create(bundle, invalidUser);
            fail("Creation should throw "
                    + PermissionDenied.class.getSimpleName());
        } catch (PermissionDenied e) {
            // We expected that permission denied... now explicitely add
            // permissions.
            PermissionGrantTarget target = manager.getFrame(
                    ContentTypes.DOCUMENTARY_UNIT.getName(),
                    PermissionGrantTarget.class);
            new AclManager(graph).grantPermission(target, PermissionType.CREATE, invalidUser
            );
            DocumentaryUnit unit = docViews.create(bundle, invalidUser);
            assertEquals(TestData.TEST_COLLECTION_NAME, unit.asVertex().getProperty("name"));
        }
    }

    @Test
    public void testCreateWithScope() throws Exception {
        Crud<DocumentaryUnit> docViews = new LoggingCrudViews<DocumentaryUnit>(
                graph, DocumentaryUnit.class, manager.getFrame("r1",
                        Repository.class));
        Bundle bundle = Bundle.fromData(TestData.getTestDocBundle());
        // In the fixtures, 'reto' should have a grant for 'CREATE'
        // scoped to the 'r1' repository.
        DocumentaryUnit unit = docViews.create(bundle, invalidUser);
        assertEquals(TestData.TEST_COLLECTION_NAME, unit.asVertex().getProperty("name"));
    }

    @Test
    public void testUserDetailAccessDenied() throws ItemNotFound {
        CrudViews<UserProfile> userViews = new CrudViews<UserProfile>(graph,
                UserProfile.class);
        userViews.detail(validUser.getId(), invalidUser);
    }

    @Test
    public void testUpdate() throws Exception {
        CrudViews<DocumentaryUnit> docViews = new CrudViews<DocumentaryUnit>(
                graph, DocumentaryUnit.class);
        Bundle bundle = Bundle.fromData(TestData.getTestDocBundle());
        DocumentaryUnit unit = docViews.create(bundle, validUser);
        assertEquals(TestData.TEST_COLLECTION_NAME, unit.asVertex().getProperty("name"));

        String newName = TestData.TEST_COLLECTION_NAME + " with new stuff";
        Bundle newBundle = bundle.withId(unit.getId()).withDataValue(
                "name", newName);

        DocumentaryUnit changedUnit = docViews.update(newBundle, validUser).getNode();
        assertEquals(newName, changedUnit.asVertex().getProperty("name"));
        DocumentDescription desc = graph.frame(
                changedUnit.getDescriptions().iterator().next().asVertex(),
                DocumentDescription.class);

        // Check the nested item was created correctly
        DatePeriod datePeriod = desc.getDatePeriods().iterator().next();
        assertTrue(datePeriod != null);
        assertEquals(TestData.TEST_START_DATE, datePeriod.getStartDate());

        // And that the reverse relationship works.
        assertEquals(desc.asVertex(), datePeriod.getEntity().asVertex());
    }

    @Test
    public void testUserUpdate() throws Exception {
        CrudViews<UserProfile> userViews = new CrudViews<UserProfile>(graph,
                UserProfile.class);
        Bundle bundle = Bundle.fromData(TestData.getTestUserBundle());
        UserProfile user = userViews.create(bundle, validUser);
        assertEquals(TestData.TEST_USER_NAME, user.getName());

        String newName = TestData.TEST_USER_NAME + " with new stuff";
        Bundle newBundle = bundle.withId(user.getId()).withDataValue(
                "name", newName);
        UserProfile changedUser = userViews.update(newBundle, validUser).getNode();
        assertEquals(newName, changedUser.getName());
    }

    @Test
    public void testUserCreate() throws Exception {
        CrudViews<UserProfile> userViews = new CrudViews<UserProfile>(graph,
                UserProfile.class);
        Bundle bundle = Bundle.fromData(TestData.getTestUserBundle());
        UserProfile user = userViews.create(bundle, validUser);
        assertEquals(TestData.TEST_USER_NAME, user.getName());
    }

    @Test
    public void testGroupCreate() throws Exception {
        CrudViews<Group> groupViews = new CrudViews<Group>(graph, Group.class);
        Bundle bundle = Bundle.fromData(TestData.getTestGroupBundle());
        Group group = groupViews.create(bundle, validUser);
        assertEquals(TestData.TEST_GROUP_NAME, group.getName());
    }

    @Test
    public void testCreateWithError() throws Exception {
        CrudViews<DocumentaryUnit> docViews = new CrudViews<DocumentaryUnit>(
                graph, DocumentaryUnit.class);
        Bundle bundle = Bundle.fromData(TestData.getTestDocBundle())
                .removeDataValue("name");

        // This shouldn't barf because the collection does not need a name.
        DocumentaryUnit unit = docViews.create(bundle, validUser);
        assertEquals(null, unit.asVertex().getProperty("name"));
    }

    @Test
    public void testDelete() throws Exception {
        CrudViews<DocumentaryUnit> docViews = new CrudViews<DocumentaryUnit>(
                graph, DocumentaryUnit.class);
        Integer shouldDelete = 1;

        // FIXME: Surely there's a better way of doing this???
        Iterator<Description> descIter = item.getDescriptions().iterator();
        for (; descIter.hasNext(); shouldDelete++) {
            DocumentDescription d = graph.frame(descIter.next().asVertex(), DocumentDescription.class);
            for (DatePeriod ignored : d.getDatePeriods()) shouldDelete++;
            for (UndeterminedRelationship ignored : d.getUndeterminedRelationships()) shouldDelete++;
        }

        Integer deleted = docViews.delete(item.getId(), validUser);
        assertEquals(shouldDelete, deleted);
    }
}
