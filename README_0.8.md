# KRUPAS Media Pro 0.8 (Roko K706)
Pelne zrodla projektu Android znajduja sie w KRUPAS_Media_Pro_0.8_source.tar.gz.
Workflow .github/workflows/build-apk.yml rozpakowuje projekt i kompiluje instalator APK z uzyciem Gradle i Android SDK 35.

- BLE GATT: automatyczne laczenie i ponawianie z ZAFIRA-DISPLAY.
- Radio FM: odczyt czestotliwosci i nazwy stacji z tekstu OEM UI (dostepnosc).
- MP3: metadane MediaSession, awaryjne broadcasty i tekst UI.
- Format ESP32: FM:, RDS:, TITLE:, MEDIA:STOP.

UWAGA: wersja testowa, bez walidacji na Roko K706. Instalator jest podpisywany kluczem debug na GitHub Actions, wiec moze wystapic konflikt podpisu przy aktualizacji.
