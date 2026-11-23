package pl.krupapiotr.mediaprobe;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects an actual call on K706 by read-only accessibility labels. */
final class PhoneUiDetector {
    private static final Pattern NUMBER=Pattern.compile("(?<![\\p{L}0-9])\\+?[0-9][0-9 ()-]{5,22}[0-9](?![\\p{L}0-9])");
    private PhoneUiDetector() {}

    // Only a recognizable number is allowed to replace the generic call label.
    // No contacts or call history access; the raw number stays out of diagnostics.
    static String firstNumber(String... labels) {
        if (labels == null) return "";
        for (String raw : labels) {
            if (raw == null) continue;
            String s = raw.trim().replaceFirst(
                    "(?iu)^(?:tel(?:efon)?|numer|number|phone)\\s*[:：]\\s*", "");
            // K706 sometimes embeds the number in a status label, e.g.
            // "Wybieranie: +48 501 234 567"; ignore resource IDs and paths.
            if (s.indexOf('/')>=0 || s.indexOf('_')>=0) continue;
            Matcher match = NUMBER.matcher(s);
            while (match.find()) {
                String number = match.group().trim();
                int digits = 0;
                for (int j = 0; j < number.length(); j++)
                    if (Character.isDigit(number.charAt(j))) digits++;
                if (digits >= 7 && digits <= 15) return number;
            }
        }
        return "";
    }

    // A contact name can be read from the *confirmed call UI* without contacts permission.
    // Never treat arbitrary text, buttons or status bar labels as caller names.
    static String firstName(String... labels) {
        if(labels==null)return "";
        for(String raw:labels) {
            if(raw==null)continue;
            String s=raw.trim().replaceAll("\\s+"," ");
            if(s.length()<2 || s.length()>56 || firstNumber(s).length()>0)continue;
            if(!s.matches(".*[\\p{L}].*") || s.matches(".*[0-9].*") ||
                    s.indexOf('/')>=0 || s.indexOf('_')>=0 || s.indexOf(':')>=0)continue;
            String lower=s.toLowerCase(Locale.ROOT);
            if(lower.matches(".*(call|phone|dial|hang|answer|reject|accept|disconnect|end call|calling|incoming|outgoing|ringing|"
                    +"rozmow|połącz|polacz|telefon|odbierz|odrzu|rozłącz|rozlacz|zakończ|zakoncz|"
                    +"wybier|dzwoni|przychodz|przychodź|kontakt|contacts|call history|systemui|"
                    +"bluetooth|mikrofon|microphone|speaker|głośn|glosn|mute|keypad|"
                    +"notific|powiadom|settings|ustawieni|czas trwania).*"))continue;
            return s;
        }
        return "";
    }

    static boolean isCandidate(String pkg) {
        if(pkg==null)return false;
        String p=pkg.toLowerCase(Locale.ROOT);
        return !p.startsWith("pl.krupapiotr.") &&
                (p.contains("bluetooth") || p.contains("btphone") || p.contains("bt_phone") ||
                 p.contains("dialer") || p.contains("incall") || p.contains("handsfree") ||
                 p.contains("callui") || p.contains("telecom") || p.contains("hfp") ||
                 p.endsWith(".bt") || p.contains(".bt.") || p.endsWith(".btui") ||
                 p.equals("com.android.systemui"));
    }

    static Result detect(String pkg,List<String> labels) {
        return detect(pkg, labels, false);
    }

    // A floating SystemUI phone panel on K706 can contain only icon buttons:
    // the OEM doesn't always expose its "wybiera..." text to Accessibility.
    static Result detect(String pkg,List<String> labels,boolean floatingPhonePanel) {
        if(pkg==null || pkg.trim().isEmpty() || pkg.startsWith("pl.krupapiotr."))
            return new Result(false,"","POMINIETE OKNO APLIKACJI",pkg,"",false,false,"","");
        final boolean knownPhonePackage=isCandidate(pkg);
        boolean end=false,accept=false,reject=false,outgoing=false,ringing=false,timer=false,ended=false;
        String number=""; int count=0;
        if(labels!=null) for(String original:labels) {
            if(original==null)continue;
            String s=original.trim();
            if(s.isEmpty())continue;
            count++;
            String t=s.toLowerCase(Locale.ROOT);
            if(any(t,"call ended","call disconnected","połączenie zakończone",
                    "polaczenie zakonczone","rozłączono rozmowę","rozlaczono rozmowe",
                    "通话结束"))ended=true;
            if(any(t,"hang up","hangup","end call","end_call","call_end",
                    "btn_end","iv_end","rozłącz","rozlacz","zakończ","zakoncz",
                    "disconnect call","terminate call","结束通话","挂断"))end=true;
            if(any(t,"answer call","answer_call","accept call","accept_call",
                    "odbierz","odpowiedz","接听"))accept=true;
            if(any(t,"decline","reject call","reject_call","odrzuć","odrzuc"))reject=true;
            if(any(t,"dialing","calling...","calling ", "outgoing call",
                    "wybieranie","wybiera","dzwonienie","połączenie wychodzące",
                    "polaczenie wychodzace","nawiązywanie połączenia",
                    "nawiazywanie polaczenia","正在呼叫","拨号中"))outgoing=true;
            if(any(t,"incoming call","przychodzące","przychodzace"))ringing=true;
            if(s.matches("[0-9]{1,2}:[0-9]{2}(:[0-9]{2})?"))timer=true;
            if (number.isEmpty()) number = firstNumber(s);
        }
        String contactName=firstName(labels==null?null:labels.toArray(new String[0]));
        // An explicit dialing status is positive evidence on known OEM BT
        // windows even when their hang-up control has no accessible label.
        boolean active=!ended&&(end||(accept&&reject) ||
                (outgoing && knownPhonePackage && !"com.android.dialer".equalsIgnoreCase(pkg)));
        // The K706 vendor phone package is not fixed across ROM releases.
        // Unknown packages need strong call-window evidence to avoid false positives.
        if(!knownPhonePackage)
            active=!ended&&((end&&(!number.isEmpty()||(timer&&(outgoing||ringing)))) ||
                    (accept&&reject&&ringing) || (outgoing&&!number.isEmpty()));
        if("com.android.systemui".equalsIgnoreCase(pkg))
            active=!ended&&((end&&(timer||!number.isEmpty()||outgoing||ringing)) ||
                    (accept&&reject&&ringing) ||
                    // K706 presents an OEM floating outgoing-call panel in SystemUI.
                    // The observed panel exposes "wybiera..." but no hang-up label
                    // to Accessibility. Require a populated window, not a lone event.
                    outgoing || floatingPhonePanel);
        // Report markers, never raw numbers, in the copyable diagnostic log.
        String diagnostic=(active?"ROZMOWA WYKRYTA":"BRAK POTWIERDZONEJ ROZMOWY")+
                " / pkg="+pkg+" / znany="+knownPhonePackage+" / elementy="+count+" / koniec="+end+
                " / odbierz="+accept+" / odrzuc="+reject+" / czas="+timer+
                " / numer="+(!number.isEmpty())+" / wybiera="+outgoing+
                " / panel="+floatingPhonePanel+" / koniec="+ended;
        return new Result(active,active?(number.isEmpty()?
                        (contactName.isEmpty()?"ROZMOWA":contactName):number):"",diagnostic,pkg,
                (knownPhonePackage || active) ? number : "",
                (knownPhonePackage && !"com.android.systemui".equalsIgnoreCase(pkg)) || active,ended,
                active ? CallDisplayPolicy.fromUi(outgoing,ringing) : "",
                active ? contactName : "");
    }

    private static boolean any(String s,String... parts) {
        for(String p:parts)if(s.contains(p))return true;
        return false;
    }
    static final class Result {
        final boolean active, phoneWindow, ended;
        final String display,diagnostic,pkg,callerHint,phase,callerName;
        Result(boolean active,String display,String diagnostic,String pkg,String callerHint,
               boolean phoneWindow,boolean ended,String phase,String callerName) {
            this.active=active;this.display=display;this.diagnostic=diagnostic;this.pkg=pkg;
            this.callerHint=callerHint;this.phoneWindow=phoneWindow;this.ended=ended;
            this.phase=phase;this.callerName=callerName;
        }
    }
}
