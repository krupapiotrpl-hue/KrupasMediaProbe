package pl.krupapiotr.mediaprobe;

/** Keeps FM station and RDS in separate, alternating five-second slots. */
final class StationRdsRotator {
    private static final long SLOT_MS = 5000L;
    private long startedAt;
    private String frequency = "";
    private String stationName = "";

    String select(long now, String freq, String station, String rds) {
        final String name = station == null ? "" : station.trim();
        final String text = rds == null ? "" : rds.trim();
        final String frequencyNow = freq == null ? "" : freq;
        if (name.isEmpty() || text.isEmpty() || name.equalsIgnoreCase(text)) {
            reset();
            return !text.isEmpty() ? text : name;
        }
        if (startedAt == 0 || now < startedAt || !frequencyNow.equals(frequency)
                || !name.equals(stationName)) {
            startedAt = now;
            frequency = frequencyNow;
            stationName = name;
        }
        return ((now - startedAt) / SLOT_MS) % 2L == 0L ? name : text;
    }

    void reset() {
        startedAt = 0;
        frequency = "";
        stationName = "";
    }
}
