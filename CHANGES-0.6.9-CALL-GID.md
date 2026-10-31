# KRUPAS Media Pro 0.6.9 — telefon na GID (test)
Data: 2026-10-31. Baza: fix/0.6.8-kp1-icons, bez nadpisania stabilnego APK.

## Zmiana
- W trakcie rozmowy rozpoznanej przez AudioManager (MODE_IN_CALL lub MODE_IN_COMMUNICATION) lub bieżące powiadomienie CATEGORY_CALL aplikacja wysyła przez BLE `CALL:<nazwa>`. Bez dostępnej nazwy wysyła `CALL:TELEFON`.
- Po zakończeniu rozmowy wysyła `CALL_END` z priorytetem przed tytułem mediów, aby ESP przywrócił poprzednie źródło.
- Kolejka komunikatów telefonicznych czeka na potwierdzenie zapisu GATT i próbuje po wznowieniu połączenia BLE. Potwierdzenie GATT **nie gwarantuje** wyświetlenia na GID.
- Nie zmieniono klasyfikacji źródeł, RDS, MP3 ani YouTube w APK.
- Zachowano formatowanie `R | ...`, `M | ...` i `> | ...`.

## Zgodność
- Firmware ESP powinien obsługiwać legacy `CALL:` i `CALL_END`; testowany przez użytkownika wariant opiera się na 1.9x17/18 i zawiera obsługę tych komend.
- Ikona telefonu na ESP powinna być zwykłą literą `T` zgodnie z konfiguracją użytkownika.
- Dla MTU 23 komunikat `CALL:TELEFON` ma 12 bajtów i mieści się w jednej wiadomości. Dłuższe nazwy mogą zostać skrócone przez `BlePayload.fit`.
- Jeśli fabryczna aplikacja BT w K706 nie zmienia Androidowego audioMode **i** nie udostępnia powiadomienia CATEGORY_CALL, wykrywanie rozmowy nie zadziała bez osobnej integracji z nią.

## Walidacja na postoju
1. Zainstalować APK 0.6.9, sprawdzić radio / MP3 / YouTube.
2. Wykonać połączenie faktycznie rozpoznane przez Android, sprawdzić diagnostykę aplikacji `CALL DETECTED`.
3. W monitorze portu szeregowego ESP (115200) oczekiwać `[BLE RX]` i `[CALL] active`; na GID `T | TELEFON` albo nazwę rozmówcy.
4. Po rozłączeniu oczekiwać `[CALL] end - restoring media`, a następnie powrót mediów.
5. Jeśli nie ma `CALL DETECTED`, problemem jest wykrycie telefonii przez system K706, a nie symbol `T`.

## Ostrzeżenia
Wersja testowa. Nie testować podczas jazdy. APK podpisane debug-key w CI może nie zainstalować się bezpośrednio na inne podpisanie — nie odinstalowywać działającej wersji bez wcześniejszej kopii i sprawdzenia podpisu.
