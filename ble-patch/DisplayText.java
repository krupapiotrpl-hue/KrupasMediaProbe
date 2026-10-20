package pl.krupapiotr.mediaprobe;

final class DisplayText {
    static String format(String pkg, String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String text = value.trim();
        String source = pkg == null ? "" : pkg.toLowerCase(java.util.Locale.ROOT);
        if (!source.contains("radio") && !source.contains(".fm")) return text;
        if (text.regionMatches(true, 0, "Radio ", 0, 6)) return "Radio " + text.substring(6).trim();
        return "Radio " + text;
    }
}
