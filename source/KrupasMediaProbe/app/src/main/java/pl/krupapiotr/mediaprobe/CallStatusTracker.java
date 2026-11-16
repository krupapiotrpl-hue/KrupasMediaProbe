package pl.krupapiotr.mediaprobe;

/** Keep an observed call alive through lost BT UI samples, until actual termination. */
final class CallStatusTracker {
    private boolean active;
    private long began;
    private long lastEvidence;
    private String reason="BRAK POTWIERDZONEJ ROZMOWY";

    boolean update(long now,boolean confirmed,long endAt){
        if(confirmed){
            if(!active)began=now;
            active=true;
            lastEvidence=now;
            reason="ROZMOWA POTWIERDZONA";
        } else if(active&&endAt>began&&endAt<=now){
            active=false;
            reason="POTWIERDZONE ROZLACZENIE";
        } else if(active){
            reason="PODTRZYMANIE - OCZEKIWANIE NA KONIEC ROZMOWY";
        } else {
            reason="BRAK POTWIERDZONEJ ROZMOWY";
        }
        return active;
    }
    String reason(){return reason;}
    long lastEvidenceAt(){return lastEvidence;}
}
