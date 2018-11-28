package eu.ehri.project.importers.links;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.importers.ImportLog;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.base.Accessible;
import eu.ehri.project.models.base.Linkable;
import eu.ehri.project.test.AbstractFixtureTest;
import eu.ehri.project.utils.Table;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LinkImporterTest extends AbstractFixtureTest {

    private final Table goodData = Table.of(ImmutableList.of(
            ImmutableList.of("r1", "c1", "", "associative", "", "Test"),
            ImmutableList.of("r1", "c1", "ur1", "associative", "", "Test 2"),
            ImmutableList.of("r4", "c4", "", "associative", "", "Test 3")
    ));

    private final Table badData = Table.of(new ImmutableList.Builder<List<String>>()
            .addAll(goodData.rows())
            .add(ImmutableList.of("BAD", "c4", "", "associative", "", "Test 2")).build());

    @Test
    public void importLinks() throws Exception {

        ImportLog log = new LinkImporter(graph, validUser, false)
                .importLinks(goodData, "testing");
        assertEquals(3, log.getCreated());
        assertTrue("Link exists",
                linkExists("r1", "c1", null, "Test"));
        assertTrue("Link exists",
                linkExists("r4", "c4", null, "Test 3"));
    }

    @Test(expected = DeserializationError.class)
    public void importLinksWithMissingTarget() throws Exception {
        new LinkImporter(graph, validUser, false)
                .importLinks(badData, "testing");
    }

    @Test
    public void importLinkWithAccessPoint() throws Exception {
        new LinkImporter(graph, validUser, false)
                .importLinks(goodData, "testing");
        assertTrue("Link exists",
                linkExists("r1", "c1", "ur1", "Test 2"));
    }

    @Test
    public void importLinksWithMissingTargetInTolerantMode() throws Exception {
        ImportLog log = new LinkImporter(graph, validUser, true)
                .importLinks(badData, "testing");
        assertEquals(3, log.getCreated());
    }

    private boolean linkExists(String srcId, String dstId, String bodyId, String text) {
        for (Link link : manager.getEntities(EntityClass.LINK, Link.class)) {
            Set<String> targets = Sets.newHashSet();
            Set<String> bodies = Sets.newHashSet();
            for (Linkable linkable : link.getLinkTargets()) {
                targets.add(linkable.getId());
            }
            for (Accessible body : link.getLinkBodies()) {
                bodies.add(body.getId());
            }
            if (targets.equals(Sets.newHashSet(srcId, dstId))
                    && (bodyId == null || bodies.contains(bodyId))
                    && (text == null || link.getDescription().equals(text))) {
                return true;
            }
        }
        return false;
    }
}