package pl.krupapiotr.mediaprobe;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Detects an actual call on K706 by read-only accessibility labels. */
final class PhoneUiDetector {
    private static final Pattern NUMBER=Pattern.compile("\\+?[0-9][0-9 ()-]{5,22}[0-9]");
    private PhoneUiDetector() {}

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
        if(pkg==null || pkg.trim().isEmpty() || pkg.startsWith("pl.krupapiotr."))
            return new Result(false,"","POMINIETE OKNO APLIKACJI",pkg);
        final boolean knownPhonePackage=isCandidate(pkg);
        boolean end=false,accept=false,reject=false,outgoing=false,ringing=false,timer=false;
        String number=""; int count=0;
        if(labels!=null) for(String original:labels) {
            if(original==null)continue;
            String s=original.trim();
            if(s.isEmpty())continue;
            count++;
            String t=s.toLowerCase(Locale.ROOT);
            if(any(t,"hang up","hangup","end call","end_call","call_end",
                    "btn_end","iv_end","rozłącz","rozlacz","zakończ","zakoncz",
                    "disconnect call","terminate call","结束通话","挂断"))end=true;
            if(any(t,"answer call","answer_call","accept call","accept_call",
                    "odbierz","odpowiedz","接听"))accept=true;
            if(any(t,"decline","reject call","reject_call","odrzuć","odrzuc"))reject=true;
            if(any(t,"dialing","calling...","outgoing call","wybieranie","dzwonienie"))outgoing=true;
            if(any(t,"incoming call","przychodzące","przychodzace"))ringing=true;
            if(s.matches("[0-9]{1,2}:[0-9]{2}(:[0-9]{2})?"))timer=true;
            if(number.isEmpty() && NUMBER.matcher(s).matches()) {
                int digits=0;for(int i=0;i<s.length();i++)if(Character.isDigit(s.charAt(i)))digits++;
                if(digits>=7 && digits<=15)number=s;
            }
        }
        boolean active=end||(accept&&reject);
        // The K706 vendor phone package is not fixed across ROM releases.
        // Unknown packages need strong call-window evidence to avoid false positives.
        if(!knownPhonePackage)
            active=(end&&(timer||!number.isEmpty()||outgoing||ringing)) ||
                    (accept&&reject&&ringing);
        if("com.android.systemui".equalsIgnoreCase(pkg))
            active=(end&&(timer||!number.isEmpty()||outgoing||ringing)) ||
                    (accept&&reject&&ringing);
        // Report markers, never raw numbers, in the copyable diagnostic log.
        String diagnostic=(active?"ROZMOWA WYKRYTA":"BRAK POTWIERDZONEJ ROZMOWY")+
                " / pkg="+pkg+" / znany="+knownPhonePackage+" / elementy="+count+" / koniec="+end+
                " / odbierz="+accept+" / odrzuc="+reject+" / czas="+timer+
                " / numer="+(!number.isEmpty());
        return new Result(active,active?(number.isEmpty()?"ROZMOWA":number):"",diagnostic,pkg);
    }

    private static boolean any(String s,String... parts) {
        for(String p:parts)if(s.contains(p))return true;
        return false;
    }
    static final class Result {
        final boolean active; final String display,diagnostic,pkg;
        Result(boolean active,String display,String diagnostic,String pkg) {
            this.active=active;this.display=display;this.diagnostic=diagnostic;this.pkg=pkg;
        }
    }
}
