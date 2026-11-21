package pl.krupapiotr.mediaprobe;

import java.util.Locale;

/** Conservative positive-evidence classifier for active call notifications. */
final class CallNotificationPolicy {
    private CallNotificationPolicy() {}
    static boolean qualifies(String category,boolean ongoing,boolean fullScreen,String title,String body) {
        if(!"call".equals(category) || (!ongoing && !fullScreen))return false;
        String t=((title==null?"":title)+" "+(body==null?"":body)).toLowerCase(Locale.ROOT);
        return !t.contains("missed call") && !t.contains("nieodebran") &&
               !t.contains("połączenie nieodebrane") && !t.contains("polaczenie nieodebrane");
    }
    static String display(String title,String body,String sub) {
        String number=PhoneUiDetector.firstNumber(title,body,sub);
        if(!number.isEmpty())return number;
        String name=PhoneUiDetector.firstName(title,body,sub);
        return name.isEmpty()?"ROZMOWA":name;
    }
}
