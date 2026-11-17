package pl.krupapiotr.mediaprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class EnabledComponentsTest {
    private final String pkg = "pl.krupapiotr.mediaprobe";
    private final String cls = pkg + ".RadioAccessibilityService";
    @Test public void acceptsFullAndShortNames() {
        assertTrue(EnabledComponents.contains(pkg + "/" + cls, pkg, cls));
        assertTrue(EnabledComponents.contains("other/other.Service:" + pkg + "/.RadioAccessibilityService", pkg, cls));
    }
    @Test public void rejectsMissingAndDifferentComponents() {
        assertFalse(EnabledComponents.contains(null, pkg, cls));
        assertFalse(EnabledComponents.contains("", pkg, cls));
        assertFalse(EnabledComponents.contains(pkg + "/.OtherService", pkg, cls));
        assertFalse(EnabledComponents.contains(pkg + ".test/" + cls, pkg, cls));
        assertFalse(EnabledComponents.contains(pkg + "/" + cls + "Extra", pkg, cls));
    }
}
