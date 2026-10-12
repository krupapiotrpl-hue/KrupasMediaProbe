# KRUPAS Media Pro 0.8 — Roko K706 / ESP32-S3 (wersja źródłowa testowa)

Cel: pobieranie tytułów MP3 i częstotliwości/nazw stacji z aplikacji radia FM oraz automatyczne przesyłanie ich do LilyGO T-2CAN (ZAFIRA-DISPLAY) przez BLE GATT.

**Status**: kompletne źródła, podstawowe testy logiki na JVM. NIE przetestowano ani kompilacji Android SDK, ani działania na prawdziwym Roko K706. APK 0.7 użytkownika nie było dostępne w postaci aktualnych źródeł, więc ta wersja została zrekonstruowana na podstawie 0.1 i znanego interfejsu ESP. Nie jest to bezpośrednia binarna poprawka aktualnie zainstalowanej aplikacji. Przed instalacją wykonaj kopię istniejącej wersji APK.

## Dlaczego dwa odczyty?
* MP3: MediaSession (tylko sesje `STATE_PLAYING`), następnie aktywne starsze broadcasty odtwarzaczy, awaryjnie ostatnio widoczny tytuł z widoku MP3 przez AccessibilityService.
* OEM FM: AccessibilityService odczytuje widoczną częstotliwość (87,5–108 MHz) i kandydat nazwy stacji; nie tworzy sztucznych nazw RDS. Odczyt z graficznych kontrolek/proprietary hardware bez informacji tekstowych jest niemożliwy bez dodatkowej integracji OEM.

## Protokół BLE — dla ESP firmware v2.1/v2.2
Nazwa BLE: ZAFIRA-DISPLAY
Service: 6fbd0001-7c40-4a16-93c5-6c0c70696f74
Write:   6fbd0002-7c40-4a16-93c5-6c0c70696f74

Wiadomości: `FM:FM 107.0`, `RDS:RMF FM`, `TITLE:Perfect - Autobiografia`, `MEDIA:STOP`, `CLEAR:FM`, `CLEAR:RDS`.

## Budowa APK
1. Otwórz folder `KrupasMediaProbe` w Android Studio z JDK 17 i Android SDK 35.
2. Poczekaj na Gradle Sync.
3. Build > Build APK(s). Plik: `app/build/outputs/apk/debug/app-debug.apk`.
4. Jeśli Android zgłosi konflikt podpisu aktualnej wersji — obie APK mają to samo `applicationId`, ale debugowe klucze mogą być różne. Sprawdź/wyeksportuj obecny APK, zanim zdecydujesz się na jego odinstalowanie.

## Konfiguracja na K706
1. Nadaj uprawnienia BLUETOOTH_SCAN + BLUETOOTH_CONNECT (Android 12+).
2. W systemie włącz dostęp do powiadomień KRUPAS Media Pro, by czytać MediaSession.
3. W Dostępności włącz `KRUPAS - odczyt ekranu radia FM`.
4. Zezwól na automatyczne uruchamianie i pracę w tle, jeśli firmware K706 posiada swoje ograniczenia oszczędzania energii.
5. Na wyświetlaczu aplikacji obserwuj `BLE`, `ŹRÓDŁO`, `MP3`, `RADIO`, `OSTATNI EKRAN FM`.
6. Testy `FM 107.0` / `MP3` klikaj po stanie BLE OK. To test łącza, nie dowód odczytu realnego RDS.

## Ograniczenia
* Accessibility nie gwarantuje odczytu nazwy stacji w aplikacji OEM FM. Jeśli widzisz częstotliwość, ale brak RDS, prześlij sekcję OSTATNI EKRAN FM, aby dopasować selektor.
* MP3 z interfejsu widokowego jest odczytywane tylko wtedy, gdy player ma dostępny tekst i jest aktywny. MediaSession jest najbardziej wiarygodna.
* Nie zmieniano ramek CAN ESP32. Czas obrotu i przewijania tekstu nadal należy do firmware ESP32.
* Wyświetlanie głośności, telefonu i nawigacji nie jest tematem tej wersji; nie przejmuj za pewnik, że zostały zachowane wszystkie funkcje starej 0.7 APK.
