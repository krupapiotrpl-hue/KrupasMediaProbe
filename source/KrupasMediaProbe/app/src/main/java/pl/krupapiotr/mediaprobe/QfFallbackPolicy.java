package pl.krupapiotr.mediaprobe;

/** BT connection fallback for K706: not a phone-call detector. */
final class QfFallbackPolicy {
    private QfFallbackPolicy() { }
    static boolean shouldShow(boolean qfConnected, boolean enabled,
                              boolean confirmedCall, boolean manualTest) {
        return qfConnected && enabled && !confirmedCall && !manualTest;
    }
}
