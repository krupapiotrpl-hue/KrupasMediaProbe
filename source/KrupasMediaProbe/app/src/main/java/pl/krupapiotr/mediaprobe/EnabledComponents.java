package pl.krupapiotr.mediaprobe;

/** Exact component match, supporting Android's full and abbreviated class names. */
final class EnabledComponents {
    static boolean contains(String enabled, String pkg, String cls) {
        if (enabled == null) return false;
        for (String item : enabled.split(":")) {
            int slash = item.indexOf('/');
            if (slash < 1 || !pkg.equals(item.substring(0, slash))) continue;
            String name = item.substring(slash + 1);
            if (name.startsWith(".")) name = pkg + name;
            if (cls.equals(name)) return true;
        }
        return false;
    }
}
