package pl.krupapiotr.mediaprobe;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/** Local, bounded snapshots of radio UI only. Never used as display metadata. */
final class RadioDiagnostics {
    private static final ArrayDeque<String> events = new ArrayDeque<>();
    private static String windows = "Brak próbki okien radia";
    private static long capturedAt, attemptedAt = -2000;
    private static int visited;

    static boolean radio(String pkg) {
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        return p.contains("radio") || p.contains(".fm");
    }
    private static String bounded(Object value, int limit) {
        String s = value == null ? "" : value.toString().replaceAll("[\\p{Cntrl}]+", " ");
        return s.length() > limit ? s.substring(0, limit) + "…" : s;
    }
    static void event(AccessibilityEvent event) {
        if (event == null || !radio(bounded(event.getPackageName(), 160))) return;
        AccessibilityNodeInfo source = null;
        String field = "brak źródła zdarzenia";
        try {
            source = event.getSource();
            if (source != null) field = bounded(source.getViewIdResourceName(), 160)
                    + " text=" + bounded(source.getText(), 240)
                    + " desc=" + bounded(source.getContentDescription(), 160);
        } catch (RuntimeException ignored) { field = "źródło niedostępne"; }
        finally { if (source != null) source.recycle(); }
        String row = "t=" + SystemClock.elapsedRealtime() / 1000 + "s "
                + bounded(event.getPackageName(), 160) + " typ=" + event.getEventType()
                + " klasa=" + bounded(event.getClassName(), 120)
                + " eventText=" + bounded(event.getText(), 320) + "\n" + field;
        if (events.size() == 12) events.removeFirst();
        events.addLast(row);
    }
    static void capture(AccessibilityService service) {
        long now = SystemClock.elapsedRealtime();
        if (now - attemptedAt < 2000) return;
        attemptedAt = now;
        StringBuilder out = new StringBuilder();
        visited = 0;
        AccessibilityNodeInfo active = null;
        try {
            active = service.getRootInActiveWindow();
            appendRoot(active, "AKTYWNY EKRAN", out);
            List<AccessibilityWindowInfo> list = service.getWindows();
            if (list != null) for (AccessibilityWindowInfo window : list) {
                if (out.length() >= 16000 || visited >= 600) break;
                AccessibilityNodeInfo root = null;
                try {
                    root = window.getRoot();
                    appendRoot(root, "OKNO typ=" + window.getType()
                            + " aktywne=" + window.isActive() + " fokus=" + window.isFocused(), out);
                } catch (RuntimeException ignored) { }
                finally { if (root != null) root.recycle(); }
            }
        } catch (RuntimeException ignored) {
            if (out.length() > 0) out.append("\nCzęść okien niedostępna");
        } finally { if (active != null) active.recycle(); }
        if (out.length() > 0) {
            windows = out.toString(); capturedAt = now;
        }
    }
    private static void appendRoot(AccessibilityNodeInfo root, String label, StringBuilder out) {
        if (root == null || !radio(bounded(root.getPackageName(), 160))) return;
        out.append("\n").append(label).append(" pakiet=").append(root.getPackageName()).append("\n");
        walk(root, out, 0);
    }
    private static void walk(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 24 || visited >= 600 || out.length() >= 16000) return;
        visited++;
        if (node.getText() != null || node.getContentDescription() != null || depth < 2) {
            out.append("id=").append(bounded(node.getViewIdResourceName(), 180))
                    .append(" klasa=").append(bounded(node.getClassName(), 100))
                    .append(" widoczne=").append(node.isVisibleToUser())
                    .append(" klikalne=").append(node.isClickable())
                    .append(" lista=").append(node.getCollectionInfo() != null)
                    .append(" text=").append(bounded(node.getText(), 240))
                    .append(" desc=").append(bounded(node.getContentDescription(), 240)).append("\n");
        }
        for (int i = 0; i < node.getChildCount() && visited < 600 && out.length() < 16000; i++) {
            AccessibilityNodeInfo child = null;
            try { child = node.getChild(i); walk(child, out, depth + 1); }
            catch (RuntimeException ignored) { }
            finally { if (child != null) child.recycle(); }
        }
    }
    static String report() {
        StringBuilder eventText = new StringBuilder();
        for (String row : events) eventText.append(row).append("\n");
        return "DIAGNOSTYKA FM — tylko próbki, nie źródło wysyłania\n"
                + (capturedAt == 0 ? "Brak próbki okien radia" : "Wiek próbki okien: "
                + (SystemClock.elapsedRealtime() - capturedAt) / 1000 + " s\n" + windows)
                + "\nOSTATNIE ZDARZENIA RADIA\n" + eventText;
    }
}
