package pl.krupapiotr.mediaprobe;

/** Suppress transient vendor UI redraws without renewing absent observations. */
final class StableTitle {
    private String owner = "", current = "", pending = "";
    private long pendingAt, seenAt;
    String update(String pkg, String candidate, long now) {
        if (!owner.equals(pkg)) {
            owner = pkg; current = ""; pending = ""; seenAt = now;
        }
        if (candidate.isEmpty()) {
            pending = "";
            if (now - seenAt >= 2500) current = "";
            return current;
        }
        if (candidate.equals(current)) {
            seenAt = now; pending = ""; return current;
        }
        if (current.isEmpty()) { current = candidate; seenAt = now; return current; }
        if (!candidate.equals(pending)) { pending = candidate; pendingAt = now; }
        if (now - pendingAt >= 600) {
            current = candidate; seenAt = now; pending = "";
        }
        return current;
    }
}
