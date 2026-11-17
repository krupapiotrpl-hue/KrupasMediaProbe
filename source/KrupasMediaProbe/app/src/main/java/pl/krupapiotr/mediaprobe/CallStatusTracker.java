package pl.krupapiotr.mediaprobe;

/**
 * Tracks calls from independent K706/Android signals.
 *
 * A short loss of HFP/UI/audio evidence must not flicker T|, but missing OEM
 * hang-up broadcasts must never leave T|ROZMOWA latched indefinitely.
 * All timestamps are wall-clock milliseconds, consistent with call observers.
 */
final class CallStatusTracker {
    static final long EVIDENCE_GRACE_MS = 6_000L;

    private boolean active;
    private long began;
    private long lastEvidence;
    private boolean waitForEvidenceToDrop;
    private String reason = "BRAK POTWIERDZONEJ ROZMOWY";

    boolean update(long now, boolean confirmed, long endAt) {
        // End events beat cached positive UI samples for the previous call.
        if (active && endAt > began && endAt <= now) {
            active = false;
            waitForEvidenceToDrop = confirmed;
            reason = "POTWIERDZONE ROZLACZENIE";
            return false;
        }
        // An old positive event may remain cached for several seconds after
        // an explicit hang-up. It must not restart T| immediately.
        if (waitForEvidenceToDrop) {
            if (!confirmed) waitForEvidenceToDrop = false;
            reason = "ROZLACZONO - OCZEKIWANIE NA NOWY SYGNAL";
            return false;
        }
        if (confirmed) {
            if (!active) began = now;
            active = true;
            lastEvidence = now;
            reason = "ROZMOWA POTWIERDZONA";
        } else if (active) {
            final long missingMs = Math.max(0L, now - lastEvidence);
            if (missingMs >= EVIDENCE_GRACE_MS) {
                active = false;
                reason = "WYGASZONO - BRAK POTWIERDZENIA PRZEZ 6 S";
            } else {
                reason = "PODTRZYMANIE - BRAK NOWEGO SYGNALU";
            }
        } else {
            reason = "BRAK POTWIERDZONEJ ROZMOWY";
        }
        return active;
    }

    String reason() { return reason; }
    long lastEvidenceAt() { return lastEvidence; }
}
