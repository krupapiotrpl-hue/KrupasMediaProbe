package pl.krupapiotr.mediaprobe;

/** A persistent DAB notification is metadata, not evidence of audio playback. */
final class MediaRoutePolicy {
    static boolean preferDab(boolean dab, boolean hasTitle, String state,
                             boolean radioLive, boolean radioVisible) {
        if (!dab || !hasTitle) return false;
        boolean weak = "DAB NOTIFICATION".equals(state) || "DAB UI".equals(state);
        return !(weak && radioLive && radioVisible);
    }
}
