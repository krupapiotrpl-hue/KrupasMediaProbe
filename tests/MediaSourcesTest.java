package pl.krupapiotr.mediaprobe;

import java.util.Arrays;
import java.util.Collections;

public class MediaSourcesTest {
    private static void eq(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        for (String invalid : new String[]{"T1", "t2", "T3", "T4", "00:12", "1:23:45",
                "00:12 / 04:30", "123", "3/17", "PLAY", "UNKNOWN TITLE"})
            eq("", MediaTitles.clean(invalid));
        eq("T10", MediaTitles.clean("T10"));
        eq("Łódź – utwór.mp3", MediaTitles.first("T1", "00:12", "Łódź – utwór.mp3"));
        eq("RDS Radio ZET", MediaTitles.clean("RDS Radio ZET"));
        if (!MediaTitles.factory("com.ts.MainUI.music") || MediaTitles.factory("com.google.android.youtube"))
            throw new AssertionError("package classification");
        SourceSelector selector = new SourceSelector();
        SourceSelector.Source mp3 = new SourceSelector.Source("com.ts.music", "MP3 title", 9999);
        SourceSelector.Source yt = new SourceSelector.Source("com.google.android.youtube", "YouTube title", 10);
        eq("MP3 title", selector.select(mp3.pkg, Arrays.asList(mp3), null).title);
        eq("YouTube title", selector.select(yt.pkg, Arrays.asList(mp3, yt), null).title);
        if (!selector.changed) throw new AssertionError("owner transition must clear lease");
        eq("YouTube title", selector.select("launcher", Arrays.asList(mp3, yt), null).title);
        // Even a newly updated stale MP3 state cannot steal YouTube ownership.
        mp3 = new SourceSelector.Source(mp3.pkg, "Old MP3", 9999999);
        eq("YouTube title", selector.select("launcher", Arrays.asList(mp3, yt), null).title);
        if (selector.select("launcher", Arrays.asList(mp3), null) != null)
            throw new AssertionError("retired MP3 returned after YouTube stopped");
        // YouTube opens before its MediaSession supplies a title.
        eq("", selector.select(yt.pkg, Arrays.asList(mp3),
                new SourceSelector.Source(yt.pkg, "", 0)).title);
        eq("Factory fallback", selector.select(mp3.pkg, Collections.emptyList(),
                new SourceSelector.Source(mp3.pkg, "Factory fallback", 0)).title);
        if (selector.select("launcher", Collections.emptyList(), null) != null)
            throw new AssertionError("removed source survived");
        System.out.println("MediaSources: filtering, fallback, source switch and stale takeover passed");
    }
}
