package pl.krupapiotr.mediaprobe;

/**
 * Tracks calls from independent K706/Android signals.
 *
 * A short loss of HFP/UI/audio evidence must not flicker T|, but missing OEM
 * hang-up broadcasts must never leave T|ROZMOWA latched indefinitely.
 * All timestamps are wall-clock milliseconds, consistent with call observers.
 */
final class CallStatusTracker {
    static final long EVIDENCE_GRACE_MS = 12_000L;
    static final long RINGING_GRACE_MS = 40_000L;
    private String phase = CallDisplayPolicy.CONNECTED;

    private boolean active;
    private long began;
    private long lastEvidence;
    private boolean waitForEvidenceToDrop;
    private String reason = "BRAK POTWIERDZONEJ ROZMOWY";

    boolean update(long now, boolean confirmed, long endAt) {
        return update(now, confirmed, endAt, CallDisplayPolicy.CONNECTED);
    }

    boolean update(long now, boolean confirmed, long endAt, String observedPhase) {
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
            phase = observedPhase;
            lastEvidence = now;
            reason = "ROZMOWA POTWIERDZONA";
        } else if (active) {
            final long missingMs = Math.max(0L, now - lastEvidence);
            final long grace = CallDisplayPolicy.DIALING.equals(phase) || CallDisplayPolicy.RINGING.equals(phase)
                    ? RINGING_GRACE_MS : EVIDENCE_GRACE_MS;
            if (missingMs >= grace) {
                active = false;
                reason = "WYGASZONO - BRAK POTWIERDZENIA PRZEZ " + (grace / 1000) + " S";
            } else {
                reason = "PODTRZYMANIE - BRAK NOWEGO SYGNALU";
            }
        } else {
            reason = "BRAK POTWIERDZONEJ ROZMOWY";
        }
        return active;
    }

    String phase() { return active ? phase : CallDisplayPolicy.CONNECTED; }
    String reason() { return reason; }
    long lastEvidenceAt() { return lastEvidence; }
}
