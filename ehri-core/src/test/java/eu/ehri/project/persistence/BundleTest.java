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

package eu.ehri.project.persistence;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.MaintenanceEventType;
import eu.ehri.project.persistence.utils.BundleUtils;
import eu.ehri.project.test.TestData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;


public class BundleTest {

    private Bundle bundle;

    @Before
    public void setUp() throws Exception {
        bundle = new Bundle(EntityClass.DOCUMENTARY_UNIT)
                .withDataValue(Ontology.IDENTIFIER_KEY, "foobar")
                .withRelation(Ontology.DESCRIPTION_FOR_ENTITY,
                        new Bundle(EntityClass.DOCUMENTARY_UNIT_DESCRIPTION)
                                .withDataValue(Ontology.NAME_KEY, "Foobar")
                                .withDataValue(Ontology.LANGUAGE, "en"));
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testCreationWithNullValues() throws Exception {
        Map<String, Object> data = Maps.newHashMap();
        data.put("identifier", null);
        Bundle b2 = new Bundle(EntityClass.DOCUMENTARY_UNIT, data);
        assertSame(data.get("identifier"), b2.getDataValue("identifier"));
    }

    @Test
    public void testGetId() throws Exception {
        assertNull(bundle.getId());
    }

    @Test
    public void testWithId() throws Exception {
        assertEquals("foo", bundle.withId("foo").getId());
    }

    @Test
    public void testGetType() throws Exception {
        assertEquals(EntityClass.DOCUMENTARY_UNIT, bundle.getType());
    }

    @Test
    public void testGetDataValue() throws Exception {
        assertEquals("foobar", bundle.getDataValue(Ontology.IDENTIFIER_KEY));
        assertNull(bundle.getDataValue(Ontology.NAME_KEY));
    }

    @Test
    public void testWithDataValue() throws Exception {
        Bundle b2 = bundle.withDataValue("testkey", "testvalue");
        assertEquals(b2.getDataValue("testkey"), "testvalue");
    }

    @Test
    public void testRemoveDataValue() throws Exception {
        Bundle b2 = bundle.withDataValue("testkey", "testvalue");
        assertEquals(b2.getDataValue("testkey"), "testvalue");
        assertNull(b2.removeDataValue("testkey").getDataValue("testkey"));
    }

    @Test
    public void testGetData() throws Exception {
        Map<String, Object> data = bundle.getData();
        assertEquals("foobar", data.get(Ontology.IDENTIFIER_KEY));
    }

    @Test
    public void testWithData() throws Exception {
        HashMap<String, Object> map = Maps.newHashMap();
        map.put("foo", "bar");
        Bundle b2 = bundle.withData(map);
        assertNull(b2.getDataValue(Ontology.IDENTIFIER_KEY));
        assertEquals("bar", b2.getDataValue("foo"));
    }

    @Test
    public void testWithDataIncludingEnumValues() throws Exception {
        HashMap<String, Object> map = Maps.newHashMap();
        map.put("foo", EventTypes.creation);
        Bundle b2 = bundle.withData(map);
        assertNull(b2.getDataValue(Ontology.IDENTIFIER_KEY));
        assertEquals(EventTypes.creation.name(), b2.getDataValue("foo"));
    }

    @Test
    public void testEqualsAndHashCode() throws Exception {
        // A bundle with the same relationship data but
        // in different orders should be equals()
        Bundle bundle1 = Bundle.fromData(TestData.getTestDocBundle());
        Bundle bundle2 = Bundle.fromData(TestData.getTestDocBundle());
        assertEquals(bundle1, bundle2);
        assertEquals(bundle1.hashCode(), bundle2.hashCode());

        // Add a new date period to both and ensure that have a
        // different structural ordering
        System.out.println(bundle1);
        Bundle dp = new Bundle(EntityClass.DATE_PERIOD)
                .withDataValue(Ontology.DATE_PERIOD_START_DATE, "1900-01-01")
                .withDataValue(Ontology.DATE_PERIOD_END_DATE, "2000-01-01");
        Bundle currentDp = BundleUtils.getBundle(bundle1, "describes[0]/hasDate[0]");
        Bundle b1_2 = BundleUtils
                .setBundle(bundle1, "describes[0]/hasDate[-1]", currentDp);
        Bundle b1_3 = BundleUtils.setBundle(b1_2, "describes[0]/hasDate[0]", dp);

        Bundle b2_2 = BundleUtils.setBundle(bundle2, "describes[0]/hasDate[-1]", dp);
        assertEquals(BundleUtils.getBundle(b1_3, "describes[0]/hasDate[1]"),
                BundleUtils.getBundle(b2_2, "describes[0]/hasDate[0]"));
        assertEquals(BundleUtils.getBundle(b1_3, "describes[0]/hasDate[0]"),
                BundleUtils.getBundle(b2_2, "describes[0]/hasDate[1]"));
        assertEquals(b1_3, b2_2);
    }

    @Test
    public void testEqualsWithManagedData() throws Exception {
        // A bundle with the same relationship data but
        // in different orders should be equals()
        Bundle bundle1 = Bundle.fromData(TestData.getTestDocBundle());
        Bundle bundle2 = Bundle.fromData(TestData.getTestDocBundle())
                .withDataValue(Bundle.MANAGED_PREFIX + "someKey", "foobar");
        assertEquals(bundle1, bundle2);

        // Data that isn't managed should count
        Bundle bundle3 = bundle1.withDataValue("foo", "bar");
        assertNotSame(bundle1, bundle3);
    }

    @Test
    public void testMergeDataWith() throws Exception {
        Bundle target = bundle.withDataValue("remove", "test");
        Bundle merge = new Bundle(target.getType())
                .withDataValue("akey", "avalue")
                .withDataValue("remove", null);
        Bundle merged = target.mergeDataWith(merge);
        assertNotSame(merged, target);
        assertEquals(merged.getDataValue(Ontology.IDENTIFIER_KEY), "foobar");
        assertEquals(merged.getDataValue("akey"), "avalue");
        assertNull(merged.getDataValue("remove"));
    }

    @Test
    public void testMergeDataWithSubtractingNullValues() throws Exception {
        Bundle merge = new Bundle(bundle.getType())
                .withDataValue("akey", "avalue");
        Bundle merged = bundle.mergeDataWith(merge);
        assertNotSame(merged, bundle);
        assertEquals(merged.getDataValue(Ontology.IDENTIFIER_KEY), "foobar");
        assertEquals(merged.getDataValue("akey"), "avalue");
    }

    @Test
    public void testMergeDataWithForTree() throws Exception {
        Bundle nested = new Bundle(EntityClass.DATE_PERIOD)
                .withDataValue(Ontology.DATE_PERIOD_START_DATE, "2001-01-01");
        // Add a nested node to the bundle we're patching and generate IDs
        Bundle start = bundle
                .withRelation(Ontology.DESCRIPTION_FOR_ENTITY,
                        new Bundle(EntityClass.MAINTENANCE_EVENT)
                                .withDataValue(Ontology.MAINTENANCE_EVENT_TYPE,
                                        MaintenanceEventType.created.toString()));

        Bundle withIds = start.generateIds(Sets.<String>newHashSet());
        Bundle toMerge = BundleUtils.setBundle(withIds, "describes[0]/hasDate[-1]", nested)
                .generateIds(Sets.<String>newHashSet());
        Bundle patch = BundleUtils.set(withIds, "describes[0]/name", "Foobar 2");
        Bundle merged = toMerge.mergeDataWith(patch);
        assertNotSame(merged, bundle);
        assertEquals("Foobar 2",
                BundleUtils.get(merged, "describes[0]/name"));
        assertEquals("created",
                BundleUtils.get(merged, "describes[1]/eventType"));
        assertEquals("2001-01-01",
                BundleUtils.get(merged, "describes[0]/hasDate[0]/startDate"));
    }

    @Test
    public void testMergeDataWithFullTree() throws Exception {
        Bundle init = Bundle.fromStream(ClassLoader.getSystemClassLoader()
                .getResourceAsStream("bundle-patch-init.json"));
        Bundle data = Bundle.fromStream(ClassLoader.getSystemClassLoader()
                .getResourceAsStream("bundle-patch-data.json"));
        Bundle result = Bundle.fromStream(ClassLoader.getSystemClassLoader()
                .getResourceAsStream("bundle-patch-result.json"));
        assertEquals(result, init.mergeDataWith(data));
    }

    @Test
    public void testFilterRelations() throws Exception {
        // Remove descriptions with languageCode = "en"
        Bundle.Filter filter = new Bundle.Filter() {
            @Override
            public boolean remove(String relationLabel, Bundle bundle) {
                String lang = bundle.getDataValue(Ontology.LANGUAGE);
                return bundle.getType().equals(EntityClass.DOCUMENTARY_UNIT_DESCRIPTION)
                        && ("en".equals(lang));
            }
        };
        Bundle filtered = bundle.filterRelations(filter);
        assertFalse((Ontology.DESCRIPTION_FOR_ENTITY).isEmpty());
        assertTrue(filtered.getRelations(Ontology.DESCRIPTION_FOR_ENTITY).isEmpty());
    }

    @Test
    public void testGetRelations() throws Exception {
        List<Bundle> relations = bundle.getRelations(Ontology.DESCRIPTION_FOR_ENTITY);
        assertEquals(1L, relations.size());
    }

    @Test
    public void testReplaceRelations() throws Exception {
        Bundle newDesc = new Bundle(EntityClass.DOCUMENTARY_UNIT_DESCRIPTION)
                .withDataValue(Ontology.NAME_KEY, "Foobar")
                .withDataValue(Ontology.LANGUAGE, "en");
        Multimap<String, Bundle> rels = ImmutableListMultimap
                .of(Ontology.DESCRIPTION_FOR_ENTITY, newDesc);
        Bundle bundle2 = bundle.replaceRelations(rels);
        assertEquals(1L, bundle2.getRelations(
                Ontology.DESCRIPTION_FOR_ENTITY).size());
    }

    @Test
    public void testWithRelationsMap() throws Exception {
        Bundle newDesc = new Bundle(EntityClass.DOCUMENTARY_UNIT_DESCRIPTION)
                .withDataValue(Ontology.NAME_KEY, "Foobar")
                .withDataValue(Ontology.LANGUAGE, "en");
        Multimap<String, Bundle> rels = ImmutableListMultimap
                .of(Ontology.DESCRIPTION_FOR_ENTITY, newDesc);
        Bundle bundle2 = bundle.withRelations(rels);
        assertEquals(2L, bundle2.getRelations(
                Ontology.DESCRIPTION_FOR_ENTITY).size());
    }

    @Test
    public void testWithRelations() throws Exception {
        Bundle newDesc = new Bundle(EntityClass.DOCUMENTARY_UNIT_DESCRIPTION)
                .withDataValue(Ontology.NAME_KEY, "Foobar")
                .withDataValue(Ontology.LANGUAGE, "en");
        Bundle bundle2 = bundle.withRelations(
                Ontology.DESCRIPTION_FOR_ENTITY, Lists.newArrayList(newDesc));
        assertEquals(2L, bundle2.getRelations(
                Ontology.DESCRIPTION_FOR_ENTITY).size());
    }

    @Test
    public void testWithRelation() throws Exception {
        Bundle newDesc = new Bundle(EntityClass.DOCUMENTARY_UNIT_DESCRIPTION)
                .withDataValue(Ontology.NAME_KEY, "Foobar")
                .withDataValue(Ontology.LANGUAGE, "en");
        Bundle bundle2 = bundle.withRelation(Ontology.DESCRIPTION_FOR_ENTITY, newDesc);
        assertEquals(2L, bundle2.getRelations(
                Ontology.DESCRIPTION_FOR_ENTITY).size());
    }

    @Test
    public void testWithMetaData() throws Exception {
        Bundle newDesc = new Bundle(EntityClass.DOCUMENTARY_UNIT_DESCRIPTION)
                .withDataValue(Ontology.NAME_KEY, "Foobar")
                .withDataValue(Ontology.LANGUAGE, "en");
        Bundle bundle2 = newDesc.withMetaDataValue("key", "val");
        Map<String, Object> meta = bundle2.getMetaData();
        assertTrue(meta.containsKey("key"));
        assertEquals("val", meta.get("key"));
    }

    @Test
    public void testHasRelations() throws Exception {
        assertTrue(bundle.hasRelations(Ontology.DESCRIPTION_FOR_ENTITY));
    }

    @Test
    public void testRemoveRelation() throws Exception {
        List<Bundle> relations = bundle.getRelations(Ontology.DESCRIPTION_FOR_ENTITY);
        assertEquals(1L, relations.size());
        Bundle bundle2 = bundle.removeRelation(
                Ontology.DESCRIPTION_FOR_ENTITY, relations.get(0));
        assertFalse(bundle2.hasRelations(Ontology.DESCRIPTION_FOR_ENTITY));
    }

    @Test
    public void testBundleDepth() throws Exception {
        assertEquals(1, bundle.depth());
        Bundle dp = new Bundle(EntityClass.DATE_PERIOD)
                .withDataValue(Ontology.DATE_PERIOD_START_DATE, "1900-01-01")
                .withDataValue(Ontology.DATE_PERIOD_END_DATE, "2000-01-01");
        Bundle deeperBundle = BundleUtils.setBundle(bundle, "describes[0]/hasDate[-1]", dp);
        assertEquals(2, deeperBundle.depth());
    }

    @Test
    public void testGetUniquePropertyKeys() throws Exception {
        Collection<String> uniquePropertyKeys = bundle.getUniquePropertyKeys();
        assertEquals(Sets.<String>newHashSet(), uniquePropertyKeys);
    }

    @Test(expected = ClassCastException.class)
    public void testGetDataValueWithBadType() throws Exception {
        Map<String, Object> dataValue = bundle.
                getDataValue(Ontology.IDENTIFIER_KEY);
        fail("Shouldn't be able to see: " + dataValue);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetDataResultIsImmutable() throws Exception {
        Map<String, Object> data = bundle.getData();
        assertNull(bundle.getDataValue("test"));
        data.put("test", "value");
    }

    @Test
    public void testImmutability() throws Exception {
        Map<String,Object> m = Maps.newHashMap();
        m.put("test", "value");
        Bundle b = bundle.withData(m);
        assertNull(b.getDataValue("test2"));
        m.put("test2", "value");
        assertNull(b.getDataValue("test2"));
    }

    @Test
    public void testNullValueHandling() throws Exception {
        Map<String,Object> m = Maps.newHashMap();
        m.put("test", "value");
        m.put("null", null);
        assertTrue(m.containsKey("null"));
        Bundle b = bundle.withData(m);
        assertNotNull(b.getDataValue("test"));
        assertNull(b.getDataValue("null"));
        assertFalse(b.getData().containsKey("null"));
    }

    @Test
    public void testGenerateIds() throws Exception {
        assertNull(bundle.getId());
        assertNull(bundle.getRelations(
                Ontology.DESCRIPTION_FOR_ENTITY).get(0).getId());
        Bundle test = bundle.generateIds(Lists.newArrayList("test"));
        assertTrue(test.hasGeneratedId());
        assertEquals("test-foobar", test.getId());
        Bundle desc = test.getRelations(Ontology.DESCRIPTION_FOR_ENTITY).get(0);
        assertNotNull(desc.getId());
        assertEquals("test-foobar.en", desc.getId());
    }
}
