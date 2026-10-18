package pl.krupapiotr.mediaprobe;

public class DisplayLeaseTest {
    private static void eq(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        DisplayLease lease = new DisplayLease();
        eq("", lease.update(null, 0));
        eq("Radio Yanosik", lease.update("Radio Yanosik", 1000));
        eq("Radio Yanosik", lease.update(null, 2000));
        eq("Radio Yanosik", lease.update(null, 4499));
        eq("", lease.update(null, 4500));
        eq("", lease.update(null, 6000));
        eq("Utwór Łódź", lease.update("Utwór Łódź", 7000));
        eq("Utwór Łódź", lease.update(null, 8000));
        eq("Nowy utwór", lease.update("Nowy utwór", 9000));
        eq("Nowy utwór", lease.update(null, 10000));
        eq("Nowy utwór", lease.update("Nowy utwór", 12499));
        eq("Nowy utwór", lease.update(null, 14000));
        eq("", lease.update(null, 16500));
        // A new source replaces the old text without an intervening clear.
        eq("Stacja A", lease.update("Stacja A", 17000));
        eq("Stacja B", lease.update("Stacja B", 17001));
        System.out.println("DisplayLease: all lifecycle scenarios passed");
    }
}
