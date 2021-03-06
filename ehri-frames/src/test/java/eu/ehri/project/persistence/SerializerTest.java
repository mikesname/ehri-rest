package eu.ehri.project.persistence;

import com.google.common.collect.Lists;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.VirtualUnit;
import eu.ehri.project.persistence.utils.BundleUtils;
import eu.ehri.project.test.AbstractFixtureTest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 *
 * TODO: Cover class more comprehensively.
 */
public class SerializerTest extends AbstractFixtureTest {

    @Test
    public void testNonLiteSerialization() throws Exception {
        DocumentaryUnit doc = manager.getFrame("c1", DocumentaryUnit.class);

        Bundle serialized = new Serializer(graph)
                .vertexFrameToBundle(doc);

        // Name of repository should be serialized
        assertEquals("Documentary Unit 1",
                BundleUtils.get(serialized, "describes[0]/name"));
        assertNotNull(BundleUtils.get(serialized, "describes[0]/scopeAndContent"));
        System.out.println(serialized);
        assertEquals("NIOD Description",
                BundleUtils.get(serialized, "heldBy[0]/describes[0]/name"));

        // But the address data shouldn't
        try {
            BundleUtils.get(serialized, "heldBy[0]/describes[0]/hasAddress[0]/streetAddress");
            fail("Default serializer should not serialize addresses in repository descriptions");
        } catch (BundleUtils.BundlePathError e) {
            // okay
        }
    }

    @Test
    public void testLiteSerialization() throws Exception {
        DocumentaryUnit doc = manager.getFrame("c1", DocumentaryUnit.class);

        Bundle serialized = new Serializer.Builder(graph).withLiteMode(true).build()
                .vertexFrameToBundle(doc);

        // Name of vu1 and repository should be serialized
        assertEquals("Documentary Unit 1",
                BundleUtils.get(serialized, "describes[0]/name"));
        // Not mandatory properties should be null...
        assertNull(BundleUtils.get(serialized, "describes[0]/scopeAndContent"));

        assertEquals("NIOD Description",
                BundleUtils.get(serialized, "heldBy[0]/describes[0]/name"));
    }

    @Test
    public void testFullSerialization() throws Exception {
        VirtualUnit vu1 = manager.getFrame("vu1", VirtualUnit.class);

        Bundle serialized = new Serializer.Builder(graph).build()
                .vertexFrameToBundle(vu1);
        
        System.out.println(serialized);
        assertEquals("vc1",
                BundleUtils.get(serialized, "isPartOf[0]/identifier"));

        // Name of includedUnit and repository should NOT be serialized, since they can be multiple
//        assertNull(BundleUtils.get(serialized, "includesUnit[0]/name"));
        
        //if it has its own description, that should be serialized:
        VirtualUnit vc1 = manager.getFrame("vc1", VirtualUnit.class);
        Bundle serializedVc1 = new Serializer.Builder(graph).build()
                .vertexFrameToBundle(vc1);
        
        assertEquals("vcd1",
                BundleUtils.get(serializedVc1, "describes[0]/identifier"));
        
    }

    @Test
    public void testMaxFetchDepth() throws Exception {
        Link link1 = manager.getFrame("link3", Link.class);
        Serializer serializer = new Serializer.Builder(graph).build();
        Bundle serialized = serializer.vertexFrameToBundle(link1);
        Bundle target0 = BundleUtils.getBundle(serialized, "hasLinkTarget[0]");
        assertEquals(1, target0.depth());
        assertEquals("c3",
                BundleUtils.get(serialized, "hasLinkTarget[0]/identifier"));
        try {
            BundleUtils.getBundle(serialized, "hasLinkTarget[0]/childOf[0]/describes[0]");
            fail("Max ifBelowLevel serialization should ignore childOf relation for item c3");
        } catch (BundleUtils.BundlePathError e) {
            // okay
        }
    }

    @Test
    public void testIncludedProperties() throws Exception {
        DocumentaryUnit doc = manager.getFrame("c1", DocumentaryUnit.class);

        Serializer serializer = new Serializer.Builder(graph).withLiteMode(true).build();
        Bundle serialized = serializer.vertexFrameToBundle(doc);

        // Name of vu1 and repository should be serialized
        assertEquals("Documentary Unit 1", BundleUtils.get(serialized, "describes[0]/name"));
        // Not mandatory properties should be null...
        assertNull(BundleUtils.get(serialized, "describes[0]/scopeAndContent"));

        Serializer withProps = serializer.withIncludedProperties(Lists.newArrayList("scopeAndContent"));
        Bundle serialized2 = withProps
                .vertexFrameToBundle(doc);
        assertNotNull(BundleUtils.get(serialized2, "describes[0]/scopeAndContent"));

        // Ensure `withCache` preserves includedProperties (#31)
        Serializer withPropsAndCache = withProps.withCache();
        assertEquals(Lists.newArrayList("scopeAndContent"),
                withPropsAndCache.getIncludedProperties());
        Bundle serialized3 = withPropsAndCache
                .vertexFrameToBundle(doc);
        assertNotNull(BundleUtils.get(serialized3, "describes[0]/scopeAndContent"));
    }
}
