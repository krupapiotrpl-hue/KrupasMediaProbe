package pl.teletechnika.canbusgesture;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;

public class MainActivity extends Activity {
    public static final String PREFS = "canbus_auto_gesture";
    private SharedPreferences prefs;
    private TextView status;
    private CheckBox automatic;
    private final int blue = Color.rgb(19, 52, 84);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 20, 30, 20);
        scroll.addView(root);
        TextView title = new TextView(this);
        title.setText("TELE TECHNIKA\nCANBUS Auto Apply 0.2");
        title.setTextSize(24);
        title.setTextColor(blue);
        root.addView(title);
        TextView info = new TextView(this);
        info.setText("\nWersja z nagrywaniem dotknięć. Nie musi rozpoznawać napisu Zapisz w fabrycznej aplikacji.\n");
        info.setTextSize(16);
        root.addView(info);
        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 8, 0, 12);
        root.addView(status);

        button(root, "1. USTAWIENIA DOSTĘPNOŚCI", () ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        button(root, "2. NAGRAJ NOWĄ SEKWENCJĘ", () -> {
            if (service() == null) { needService(); return; }
            // Recording starts from the HOME screen. Floating controls remain visible.
            service().startRecording();
            toast("Na pulpicie radia użyj pływającego przycisku DODAJ PUNKT.");
            refresh();
        });

        button(root, "3. TEST ZAPISANEJ SEKWENCJI", () -> {
            if (service() == null) { needService(); return; }
            service().runScript(true);
            toast("Sprawdź, czy CANBUS faktycznie się wczytał.");
        });

        automatic = new CheckBox(this);
        automatic.setText("Automatycznie 8 s po wybudzeniu ekranu");
        automatic.setTextSize(16);
        automatic.setOnCheckedChangeListener((v, on) -> {
            if (on && count() == 0) {
                toast("Najpierw nagraj i przetestuj sekwencję.");
                v.setChecked(false);
                return;
            }
            prefs.edit().putBoolean("auto", on).apply();
            refresh();
        });
        root.addView(automatic);

        button(root, "WYŁĄCZ AUTOMATYKĘ / PRZERWIJ", () -> {
            prefs.edit().putBoolean("auto", false).apply();
            if (service() != null) service().stopEverything();
            refresh();
        });
        button(root, "SKASUJ SEKWENCJĘ", () -> {
            if (service() != null) service().stopEverything();
            prefs.edit().remove("steps").putBoolean("auto", false)
                .putString("last", "Sekwencja usunięta.").apply();
            refresh();
        });
        TextView explanation = new TextView(this);
        explanation.setText("\nJAK NAGRAĆ: uruchom nagrywanie. Radio przejdzie na ekran główny. Użyj pływającego DODAJ PUNKT, dotknij ikony otwierającej ustawienia pojazdu, odczekaj na otwarcie ekranu, ponownie DODAJ PUNKT i dotknij kolejnego elementu. Powtarzaj aż do przycisku Zapisz. Naciśnij GOTOWE na pływającym pasku.\n\nWAŻNE: Nagrywaj tylko podczas postoju. W czasie testu aplikacja steruje ekranem. Automatyka jest wyłączona, dopóki jej sam nie włączysz. Przy zmianie układu ekranu trzeba nagrać sekwencję od nowa.");
        explanation.setTextSize(15);
        root.addView(explanation);
        setContentView(scroll);
        refresh();
    }

    private void button(LinearLayout root, String label, Runnable click) {
        Button b = new Button(this);
        b.setText(label);
        root.addView(b);
        b.setOnClickListener(v -> click.run());
    }
    private GestureService service() { return GestureService.instance; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private void needService() { toast("Najpierw włącz CANBUS Auto 0.2 w ustawieniach dostępności."); }
    private int count() {
        try { return new JSONArray(prefs.getString("steps", "[]")).length(); }
        catch (Exception e) { return 0; }
    }

    @Override protected void onResume() { super.onResume(); if (status != null) refresh(); }
    private void refresh() {
        status.setText("Usługa 0.2: " + (service() == null ? "NIEAKTYWNA" : "AKTYWNA")
            + "\nZapisane dotknięcia: " + count()
            + "\nOstatni ekran: " + prefs.getString("screen", "brak danych")
            + "\nOstatni wynik: " + prefs.getString("last", "Brak testów."));
        automatic.setOnCheckedChangeListener(null);
        automatic.setChecked(prefs.getBoolean("auto", false));
        automatic.setOnCheckedChangeListener((v, on) -> {
            if (on && count() == 0) {
                toast("Najpierw nagraj i przetestuj sekwencję.");
                v.setChecked(false); return;
            }
            prefs.edit().putBoolean("auto", on).apply();
            refresh();
        });
    }
}
