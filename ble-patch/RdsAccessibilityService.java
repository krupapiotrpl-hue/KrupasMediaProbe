package pl.krupapiotr.mediaprobe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class RdsAccessibilityService extends AccessibilityService {
    private BleDisplayClient ble;
    private static RdsAccessibilityService instance;
    static String stationPackage = "";
    static String stationTitle = "";

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        try {
            AccessibilityServiceInfo info = getServiceInfo();

            if (info == null) {
                info = new AccessibilityServiceInfo();
            }

            info.eventTypes =
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;

            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 150;

            info.flags |=
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

            setServiceInfo(info);

        } catch (Throwable ignored) {}

        ble = BleDisplayClient.get(this);
        ble.start();

        try {
            MediaBridgeService.start(this);
        } catch (Throwable ignored) {}
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // MediaBridgeService is the single writer. It samples the actual active
        // window every second, including when a radio sends no new UI events.
    }

    @Override public void onInterrupt() { resetStation(); }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        resetStation();
        super.onDestroy();
    }

    private static void resetStation() {
        stationPackage = "";
        stationTitle = "";
    }

    static String readVisibleStation() {
        if (instance == null) { resetStation(); return ""; }
        AccessibilityNodeInfo root = null;
        try {
            root = instance.getRootInActiveWindow();
            if (root == null || root.getPackageName() == null) { resetStation(); return ""; }
            String pkg = root.getPackageName().toString();
            String lower = pkg.toLowerCase(Locale.ROOT);
            // A system overlay is not evidence that the radio closed; the lease
            // tolerates a brief absence without renewing an unobserved source.
            if (lower.equals("com.android.systemui")) return "";
            if (!lower.contains("radio") && !lower.contains(".fm")) {
                resetStation();
                return "";
            }
            if (!pkg.equals(stationPackage)) resetStation();
            stationPackage = pkg;
            Set<String> texts = new LinkedHashSet<>();
            instance.collect(root, texts, 0);
            String candidate = instance.chooseBestRadioCandidate(texts);
            if (!candidate.isEmpty()) stationTitle = candidate;
            // Retain RDS through frequency-only updates, but only while the same
            // radio window is actually present. Never fall back to an old event.
            return stationTitle;
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (root != null) try { root.recycle(); } catch (Throwable ignored) {}
        }
    }
    private void collect(
            AccessibilityNodeInfo node,
            Set<String> out,
            int depth
    ) {
        if (node == null || depth > 20 || out.size() > 120) return;

        try {
            addText(out, node.getText());
        } catch (Throwable ignored) {}

        try {
            addText(out, node.getContentDescription());
        } catch (Throwable ignored) {}

        int count;

        try {
            count = node.getChildCount();
        } catch (Throwable ignored) {
            return;
        }

        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = null;

            try {
                child = node.getChild(i);

                if (child != null) {
                    collect(child, out, depth + 1);
                }

            } catch (Throwable ignored) {
                // Pojedynczy zly wezel nie moze ubic calej uslugi.

            } finally {
                if (child != null) {
                    try {
                        child.recycle();
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    private void addText(Set<String> out, CharSequence value) {
        if (value == null) return;

        String s = value.toString()
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();

        while (s.contains("  ")) {
            s = s.replace("  ", " ");
        }

        if (!s.isEmpty()) out.add(s);
    }

    private String chooseBestRadioCandidate(Set<String> texts) {
        String best = "";
        int bestScore = Integer.MIN_VALUE;

        for (String raw : texts) {
            String s = sanitize(raw);

            if (s.length() < 3 || s.length() > 64) continue;

            String u = s.toUpperCase(Locale.ROOT);

            if (!containsLetter(s)) continue;
            if (looksLikeFrequency(u)) continue;
            if (isUiWord(u)) continue;

            int score = radioScore(u);

            if (score < 60) continue;

            if (s.length() >= 4 && s.length() <= 28) score += 20;
            if (s.indexOf(' ') >= 0) score += 8;

            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }

        return best;
    }

    private int radioScore(String u) {
        int score = 0;

        if (u.contains("RADIO")) score += 120;
        if (u.contains("RMF")) score += 100;
        if (u.contains("ZET")) score += 90;
        if (u.contains("ESKA")) score += 90;
        if (u.contains("VOX")) score += 80;
        if (u.contains("TOK FM")) score += 100;
        if (u.contains("ANTYRADIO")) score += 100;
        if (u.contains("MELO")) score += 80;
        if (u.contains("PLUS")) score += 65;
        if (u.contains("CHILLI")) score += 80;
        if (u.contains("MARYJA")) score += 80;
        if (u.contains("357")) score += 65;
        if (u.contains("NOWY SWIAT")) score += 80;
        if (u.contains("NOWY ŚWIAT")) score += 80;

        return score;
    }

    private boolean containsLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) return true;
        }

        return false;
    }

    private boolean looksLikeFrequency(String u) {
        String x = u.replace("MHZ", "")
                .replace("FM", "")
                .replace("AM", "")
                .replace(" ", "")
                .replace(",", ".")
                .trim();

        if (x.isEmpty()) return true;

        try {
            Double.parseDouble(x);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isUiWord(String u) {
        String[] blocked = {
                "FM", "AM", "RDS", "PTY", "TA", "AF", "ST", "STEREO",
                "LOC", "DX", "SCAN", "BAND", "EQ", "HOME", "BACK",
                "NEXT", "PREV", "PREVIOUS", "PLAY", "PAUSE", "SETTINGS",
                "USTAWIENIA", "SEARCH", "SZUKAJ", "VOLUME", "GLOSNOSC",
                "CENTRUM", "MENU", "SOURCE", "ZRODLO", "ŹRÓDŁO"
        };

        for (String b : blocked) {
            if (u.equals(b)) return true;
        }

        return false;
    }

    private String sanitize(String text) {
        if (text == null) return "";

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < text.length() && out.length() < 96; i++) {
            char c = text.charAt(i);

            if (c == '\n' || c == '\r' || c == '\t') c = ' ';

            if (!Character.isISOControl(c)) {
                out.append(c);
            }
        }

        String result = out.toString().trim();

        while (result.contains("  ")) {
            result = result.replace("  ", " ");
        }

        return result;
    }
}

