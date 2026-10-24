package pl.krupapiotr.mediaprobe;

public final class IconProtocolTest {
    public static void main(String[] args) {
        eq("@KP1|SRC|RADIO|RMF%20FM", IconProtocol.source("RADIO", "RMF FM"));
        eq("@KP1|CALL|ACTIVE|Ania", IconProtocol.call("Ania"));
        eq("@KP1|CALL|ACTIVE|%2B48%20123", IconProtocol.call("+48 123"));
        eq("@KP1|SRC|NAV|skr%C4%99%C4%87%7Cteraz", IconProtocol.source("NAV", "skręć|teraz"));
        eq("@KP1|CALL|END|", IconProtocol.callEnd());
        System.out.println("IconProtocolTest OK");
    }
    private static void eq(String expected, String actual) {
        if (!expected.equals(actual))
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
