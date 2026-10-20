package pl.krupapiotr.mediaprobe;

/** Short-lived observations from explicit PS/RT accessibility events only. */
final class RadioRdsCache {
    private String pkg = "", title = "";
    private long observedAt;
    void observe(String source, String value, long now) {
        pkg = source; title = value; observedAt = now;
    }
    String read(String foreground, long now) {
        return pkg.equals(foreground) && now - observedAt <= 2500 ? title : "";
    }
    void clear() { pkg = ""; title = ""; }
}
