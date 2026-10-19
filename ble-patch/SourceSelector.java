package pl.krupapiotr.mediaprobe;

import java.util.List;

/** A foreground playing source owns the display until it stops or another
 * foreground playing source takes over. Old position timestamps cannot steal it. */
final class SourceSelector {
    static final class Source {
        final String pkg, title;
        final long updated;
        Source(String pkg, String title, long updated) {
            this.pkg = pkg; this.title = title; this.updated = updated;
        }
    }
    private String owner = "";
    private final java.util.Set<String> retired = new java.util.HashSet<>();
    boolean changed;

    Source select(String foreground, List<Source> active, Source visible) {
        Source chosen = null;
        for (Source s : active) if (s.pkg.equals(foreground)) { chosen = s; break; }
        if (chosen == null && visible != null) chosen = visible;
        if (chosen == null) for (Source s : active) if (s.pkg.equals(owner)) { chosen = s; break; }
        if (chosen == null) for (Source s : active)
            if (!retired.contains(s.pkg) && (chosen == null || s.updated > chosen.updated)) chosen = s;
        String next = chosen == null ? "" : chosen.pkg;
        changed = !next.equals(owner);
        if (changed && !owner.isEmpty()) retired.add(owner);
        if (!next.isEmpty()) retired.remove(next);
        owner = next;
        return chosen;
    }
}
