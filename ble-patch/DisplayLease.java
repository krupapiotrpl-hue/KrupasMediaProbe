package pl.krupapiotr.mediaprobe;

/** Only freshly observed sources renew the display. Uses a monotonic clock. */
final class DisplayLease {
    static final long GRACE_MS = 2500;
    private String text = "";
    private long missingSince = -1;

    String update(String freshText, long now) {
        if (freshText != null && !freshText.trim().isEmpty()) {
            text = freshText;
            missingSince = -1;
        } else {
            if (missingSince < 0) missingSince = now;
            if (now - missingSince >= GRACE_MS) text = "";
        }
        return text;
    }
}
