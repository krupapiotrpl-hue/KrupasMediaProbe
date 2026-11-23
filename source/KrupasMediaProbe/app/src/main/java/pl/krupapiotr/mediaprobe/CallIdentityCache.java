package pl.krupapiotr.mediaprobe;

/**
 * Remembers caller identity for a single confirmed call.
 *
 * K706's OEM Bluetooth UI may expose a caller number or contact name only
 * briefly, then replace it with icon-only controls. Caller identity should
 * not expire on the 4/6-second sampling windows while the call is confirmed.
 *
 * Do not persist identity to disk, diagnostics or another call.
 */
final class CallIdentityCache {
    private String number = "";
    private String name = "";

    static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot("", "", false, false);
        final String number, name;
        final boolean numberCached, nameCached;
        Snapshot(String number, String name, boolean numberCached, boolean nameCached) {
            this.number = number;
            this.name = name;
            this.numberCached = numberCached;
            this.nameCached = nameCached;
        }
    }

    Snapshot update(String freshNumber, String freshName) {
        String n = PhoneUiDetector.firstNumber(freshNumber);
        String label = PhoneUiDetector.firstName(freshName);
        boolean numberCached = n.isEmpty() && !number.isEmpty();
        boolean nameCached = label.isEmpty() && !name.isEmpty();
        if (!n.isEmpty()) {
            // Do not keep a name belonging to a different phone number.
            if (!number.isEmpty() && !sameNumber(number, n)) name = "";
            number = n;
        }
        if (!label.isEmpty()) name = label;
        return new Snapshot(number, name, numberCached, nameCached && !name.isEmpty());
    }

    void clear() {
        number = "";
        name = "";
    }

    private static boolean sameNumber(String a, String b) {
        return a.replaceAll("[^0-9]", "").equals(b.replaceAll("[^0-9]", ""));
    }
}
