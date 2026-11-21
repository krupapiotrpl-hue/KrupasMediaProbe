package pl.krupapiotr.mediaprobe;

import java.util.Locale;

/**
 * Display labels for confirmed calls. The ESP owns the "T|" prefix, so this
 * policy returns only the payload for the existing PHONE: BLE protocol.
 */
final class CallDisplayPolicy {
    static final String DIALING = "DIALING";
    static final String RINGING = "RINGING";
    static final String CONNECTED = "CONNECTED";

    private CallDisplayPolicy() {}

    static String fromHfpState(int state) {
        // BluetoothHeadsetClientCall: active=0, held=1, dialing=2,
        // alerting=3, incoming=4, waiting=5, held-by-response=6, ended=7.
        if (state == 2 || state == 3) return DIALING;
        if (state == 4 || state == 5) return RINGING;
        if (state == 0 || state == 1 || state == 6) return CONNECTED;
        return "";
    }

    static String fromUi(boolean outgoing, boolean incoming) {
        if (outgoing) return DIALING;
        if (incoming) return RINGING;
        return CONNECTED;
    }

    static String fromNotification(String title, String body, String sub) {
        String t = ((title == null ? "" : title) + " " +
                (body == null ? "" : body) + " " +
                (sub == null ? "" : sub)).toLowerCase(Locale.ROOT);
        if (t.contains("incoming call") || t.contains("przychodz") ||
                t.contains("ringing")) return RINGING;
        if (t.contains("outgoing call") || t.contains("dialing") ||
                t.contains("wybier") || t.contains("calling...")) return DIALING;
        // Notification.CATEGORY_CALL alone does not distinguish call phases.
        return CONNECTED;
    }

    static String select(boolean hfpActive, String hfpPhase,
                         boolean uiActive, String uiPhase,
                         boolean localRinging,
                         boolean notificationActive, String notificationPhase) {
        if (hfpActive && valid(hfpPhase)) return hfpPhase;
        if (uiActive && valid(uiPhase)) return uiPhase;
        if (localRinging) return RINGING;
        if (notificationActive && valid(notificationPhase)) return notificationPhase;
        // Local OFFHOOK / audio evidence may include dialing. Without explicit
        // phase information, avoid claiming a number or a dialing transition.
        return CONNECTED;
    }

    static String display(String phase, String confirmedNumber, boolean test) {
        return display(phase, confirmedNumber, "", test);
    }

    static String display(String phase, String confirmedNumber, String confirmedName, boolean test) {
        if (test) return "TEST TELEFONU";
        if (DIALING.equals(phase)) return "WYBIERANIE";
        if (RINGING.equals(phase)) return "POŁĄCZENIE";
        // A name from the confirmed call window or call notification wins
        // over the number. Never use an arbitrary accessibility label.
        String name = PhoneUiDetector.firstName(confirmedName);
        if (!name.isEmpty()) return name;
        String number = PhoneUiDetector.firstNumber(confirmedNumber);
        return number.isEmpty() ? "ROZMOWA" : number;
    }

    private static boolean valid(String phase) {
        return DIALING.equals(phase) || RINGING.equals(phase) ||
                CONNECTED.equals(phase);
    }
}
