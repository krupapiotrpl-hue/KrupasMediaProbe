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
    private String lastSent = "";

    @Override public void onServiceConnected() {
        super.onServiceConnected();

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
        try {
            handleAccessibilityEvent(event);
        } catch (Throwable ignored) {
            // Na niektorych ROM-ach K706 pojedynczy uszkodzony/recyklingowany
            // AccessibilityNodeInfo potrafi rzucic wyjatek. Nie pozwalamy,
            // aby taki przypadek zabil caly KRUPAS RDS Reader.
            if (ble == null) ble = BleDisplayClient.get(this);
            ble.start();
        }
    }

    private void handleAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        CharSequence pkgCs = event.getPackageName();
        String pkg = pkgCs == null ? "" : pkgCs.toString();

        if (pkg.equals(getPackageName())) return;

        AccessibilityNodeInfo root = null;

        try {
            root = getRootInActiveWindow();
        } catch (Throwable ignored) {}

        if (root == null) {
            try {
                root = event.getSource();
            } catch (Throwable ignored) {}
        }

        if (root == null) return;

        Set<String> texts = new LinkedHashSet<>();

        try {
            collect(root, texts, 0);
        } catch (Throwable ignored) {
            return;
        }

        // Only trust the foreground window from known media applications.
        String activePkg = pkg;
        try {
            AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active != null && active.getPackageName() != null)
                activePkg = active.getPackageName().toString();
        } catch (Throwable ignored) {}
        if (!pkg.equals(activePkg)) return;

        boolean mp3 = "com.qf.musicplayer".equals(pkg);
        boolean radio = "com.navimods.radio".equals(pkg);
        if (!mp3 && !radio) return;

        String best = mp3 ? chooseMp3Title(texts) : chooseBestRadioCandidate(texts);

        if (!best.isEmpty() && !best.equals(lastSent)) {
            lastSent = best;

            MediaStateStore.save(
                    this,
                    "RDS_ACCESSIBILITY",
                    pkg,
                    best,
                    "",
                    "",
                    "VISIBLE",
                    "filtered-radio-candidate"
            );

            if (ble == null) ble = BleDisplayClient.get(this);

            ble.start();
            ble.sendText(best);
        }
    }

    @Override public void onInterrupt() {
        if (ble == null) ble = BleDisplayClient.get(this);
        ble.start();
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

    private String chooseMp3Title(Set<String> texts) {
        String best = "";
        int bestScore = Integer.MIN_VALUE;
        for (String raw : texts) {
            String s = sanitize(raw);
            String u = s.toUpperCase(Locale.ROOT);
            if (s.length() < 4 || s.length() > 96 || !containsLetter(s)) continue;
            if (looksLikeFrequency(u) || isUiWord(u)) continue;
            if (u.equals("RADIO") || u.equals("MUSIC") || u.equals("MP3")
                    || u.equals("USB") || u.equals("LOCAL MUSIC")
                    || u.equals("UNKNOWN") || u.equals("ALBUM")
                    || u.equals("ARTIST") || u.equals("TITLE")) continue;
            if (s.matches(".*[0-9]{1,2}:[0-9]{2}.*")) continue;
            int score = s.length();
            if (s.indexOf(' ') >= 0) score += 20;
            if (u.endsWith(".MP3") || u.endsWith(".FLAC") || u.endsWith(".WAV") || u.endsWith(".M4A")) score += 25;
            if (score > bestScore) { bestScore = score; best = s; }
        }
        return best;
    }

    private String chooseBestRadioCandidate(Set<String> texts) {
        String best = "";
        int bestScore = Integer.MIN_VALUE;

        for (String raw : texts) {
            String s = sanitize(raw);

            if (s.length() < 3 || s.length() > 64 || s.equalsIgnoreCase("Radio")) continue;

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
