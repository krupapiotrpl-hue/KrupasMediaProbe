package pl.teletechnika.canbusauto;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String PREFS = "canbus_auto";
    private TextView status;
    private CheckBox enabled;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 28, 28, 28);
        scroll.addView(layout);
        TextView title = new TextView(this);
        title.setText("TELE TECHNIKA\nCANBUS Auto Apply 0.1");
        title.setTextSize(23);
        title.setTextColor(Color.rgb(20, 49, 76));
        layout.addView(title);
        TextView intro = new TextView(this);
        intro.setText("\nWersja testowa dla Roco K706 / Zafira B. Nie zmienia wyboru dekodera Simple OP-SS-01. Próbuje jedynie ponownie nacisnąć zapisany przycisk Zapisz.\n");
        intro.setTextSize(15);
        layout.addView(intro);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, 16, 0, 16);
        layout.addView(status);

        addButton(layout, "1. Włącz usługę dostępności", () ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        addButton(layout, "2. Naucz przycisku ZAPISZ", () -> {
            prefs.edit().putBoolean("learning", true).putBoolean("auto", false)
                .remove("pkg").remove("activity").remove("saveId").apply();
            refresh();
            Toast.makeText(this, "Otwórz CANBUS i ręcznie naciśnij Zapisz. Następnie wróć tutaj.", Toast.LENGTH_LONG).show();
        });

        enabled = new CheckBox(this);
        enabled.setText("Automatycznie po wybudzeniu ekranu");
        enabled.setTextSize(16);
        enabled.setOnCheckedChangeListener((b, checked) -> {
            if (checked && prefs.getString("pkg", "").isEmpty()) {
                Toast.makeText(this, "Najpierw naucz przycisku Zapisz.", Toast.LENGTH_LONG).show();
                b.setChecked(false);
                return;
            }
            prefs.edit().putBoolean("auto", checked).apply();
            refresh();
        });
        layout.addView(enabled);

        addButton(layout, "3. TEST TERAZ (tylko na postoju)", () -> {
            if (CanbusAccessibilityService.instance == null) {
                Toast.makeText(this, "Najpierw włącz usługę dostępności.", Toast.LENGTH_LONG).show();
                return;
            }
            CanbusAccessibilityService.instance.testNow();
            Toast.makeText(this, "Próba uruchomienia zapisanego ekranu CANBUS", Toast.LENGTH_SHORT).show();
        });

        addButton(layout, "Wyłącz automatykę", () -> {
            prefs.edit().putBoolean("auto", false).putBoolean("learning", false).apply();
            refresh();
        });

        TextView warning = new TextView(this);
        warning.setPadding(0, 16, 0, 0);
        warning.setText("UWAGA: Test wykonuj wyłącznie na postoju. Aplikacja może otwierać fabryczne ustawienia na wierzchu. Nie gwarantuje skuteczności bez znajomości API producenta. Jeśli CANBUS działa, nie trzeba niczego zmieniać.");
        layout.addView(warning);
        setContentView(scroll);
        refresh();
    }

    private void addButton(LinearLayout root, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        root.addView(b);
        b.setOnClickListener(v -> action.run());
    }

    @Override public void onResume() {
        super.onResume();
        if (status != null) refresh();
    }

    private void refresh() {
        String pkg = prefs.getString("pkg", "");
        String last = prefs.getString("last", "Brak prób.");
        status.setText("Usługa: " + (CanbusAccessibilityService.instance != null ? "AKTYWNA" : "NIEAKTYWNA")
            + "\nNauka: " + (prefs.getBoolean("learning", false) ? "TRWA – przejdź do CANBUS i wciśnij Zapisz" : "wyłączona")
            + "\nZapamiętana aplikacja: " + (pkg.isEmpty() ? "brak" : pkg)
            + "\nOstatni wynik: " + last);
        if (enabled != null) {
            enabled.setOnCheckedChangeListener(null);
            enabled.setChecked(prefs.getBoolean("auto", false));
            enabled.setOnCheckedChangeListener((b, checked) -> {
                if (checked && prefs.getString("pkg", "").isEmpty()) {
                    Toast.makeText(this, "Najpierw naucz przycisku Zapisz.", Toast.LENGTH_LONG).show();
                    b.setChecked(false);
                    return;
                }
                prefs.edit().putBoolean("auto", checked).apply();
                refresh();
            });
        }
    }
}
