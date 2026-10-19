package pl.krupapiotr.mediaprobe;

import java.nio.charset.StandardCharsets;

/** One complete UTF-8 payload per ESP title. Avoid a silent total loss at MTU 23. */
final class BlePayload {
    static String fit(String text, int budget) {
        if (text.getBytes(StandardCharsets.UTF_8).length <= budget) return text;
        StringBuilder result = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String next = new String(Character.toChars(cp));
            int size = next.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > budget) break;
            result.append(next); bytes += size; i += Character.charCount(cp);
        }
        return result.toString();
    }
}
