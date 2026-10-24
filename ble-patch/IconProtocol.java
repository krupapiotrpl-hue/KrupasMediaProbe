package pl.krupapiotr.mediaprobe;

import java.nio.charset.StandardCharsets;

/**
 * Candidate versioned BLE frames. NOT transmitted to deployed ESP firmware.
 * Only activate following tested capability negotiation on both endpoints.
 */
final class IconProtocol {
    private IconProtocol() {}

    static String source(String kind, String label) {
        if (!("RADIO".equals(kind) || "YOUTUBE".equals(kind)
                || "MP3".equals(kind) || "NAV".equals(kind)))
            throw new IllegalArgumentException("Unsupported source");
        return "@KP1|SRC|" + kind + "|" + escape(label);
    }

    static String call(String caller) {
        return "@KP1|CALL|ACTIVE|" + escape(caller);
    }

    static String callEnd() {
        return "@KP1|CALL|END|";
    }

    // Percent encoding is byte-based UTF-8; no control delimiters leak.
    static String escape(String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder(bytes.length);
        final char[] hex = "0123456789ABCDEF".toCharArray();
        for (byte b : bytes) {
            int c = b & 0xff;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_'
                    || c == '.' || c == '~') {
                result.append((char)c);
            } else {
                result.append('%').append(hex[c >>> 4]).append(hex[c & 15]);
            }
        }
        return result.toString();
    }
}
