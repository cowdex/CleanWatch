package tv.cleanwatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class AdBlockerTest {

    private static AdBlocker blocker(String list) throws Exception {
        AdBlocker b = new AdBlocker();
        b.load(new ByteArrayInputStream(list.getBytes(StandardCharsets.UTF_8)));
        return b;
    }

    @Test
    public void readsPlainHostsFileAndAdblockForms() throws Exception {
        AdBlocker b = blocker("# comment\n"
                + "ads.example.com\n"
                + "0.0.0.0 tracker.example.net   # trailing comment\n"
                + "127.0.0.1\tpop.example.org\n"
                + "||cdn.adnet.io^\n"
                + "! adblock comment\n"
                + "\n"
                + "localhost\n");
        assertEquals(4, b.size());
        assertTrue(b.matches("ads.example.com"));
        assertTrue(b.matches("tracker.example.net"));
        assertTrue(b.matches("pop.example.org"));
        assertTrue(b.matches("cdn.adnet.io"));
    }

    @Test
    public void blocksSubdomainsButNotSiblingsOrParents() throws Exception {
        AdBlocker b = blocker("ads.example.com\nadnet.io\n");
        assertTrue(b.matches("a.b.ads.example.com"));
        assertTrue(b.matches("ADS.Example.COM"));
        assertTrue(b.matches("x.adnet.io"));
        assertFalse(b.matches("example.com"));
        assertFalse(b.matches("www.example.com"));
        assertFalse(b.matches("notadnet.io"));
        assertFalse(b.matches("io"));
        assertFalse(b.matches(null));
    }

    @Test
    public void ignoresLinesThatAreNotHosts() {
        assertNull(AdBlocker.parse("0.0.0.0 0.0.0.0"));
        assertNull(AdBlocker.parse("example.com/path"));
        assertNull(AdBlocker.parse("com"));
        assertNull(AdBlocker.parse("   "));
        assertEquals("a.example.com", AdBlocker.parse("  A.Example.com  "));
    }
}
