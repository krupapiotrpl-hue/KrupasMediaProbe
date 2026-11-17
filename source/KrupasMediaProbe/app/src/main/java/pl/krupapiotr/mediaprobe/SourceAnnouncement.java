package pl.krupapiotr.mediaprobe;

import java.util.ArrayList;
import java.util.List;

/** Emits an ordered source boundary before metadata, including an empty FM title. */
final class SourceAnnouncement {
    private String previous = "";
    private long announced;

    void reset() { previous = ""; announced = 0; }

    List<String> commands(String next, long now) {
        List<String> out = new ArrayList<>();
        if (!next.equals(previous)) {
            // CLEAR:ALL is understood by ESP 1.9x17 and 1.9x21. It clears
            // source-specific cached text as well as the currently rendered title.
            out.add("CLEAR:ALL");
            out.add("SOURCE:" + next);
        } else if (now - announced >= 10000) {
            out.add("SOURCE:" + next);
        }
        if (!out.isEmpty()) { previous = next; announced = now; }
        return out;
    }
}
