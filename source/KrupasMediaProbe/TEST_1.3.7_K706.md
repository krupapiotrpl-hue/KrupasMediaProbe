# KRUPAS Media Pro 1.3.7 — K706, wersja testowa

Baza: gałąź `fix/kmp-136-audio-call-detection-20261110`, commit `d1aa854df1cf16747ead06e5252e859eb08aef1e`.

## Zmiany

- Przy zmianie źródła aplikacja wysyła `CLEAR:ALL`, następnie `SOURCE:FM`, `SOURCE:DAB`, `SOURCE:MP3`, `SOURCE:YT` lub `SOURCE:OFF`, a dopiero później aktualny tekst. FM jest wybierane także bez RDS. Dane bieżącego źródła są ponawiane po wyczyszczeniu cache i po ponownym połączeniu BLE. Stabilne źródło nie jest ponownie kasowane przy każdym odświeżeniu.
- Pozostawione powiadomienie DAB-Z i zapamiętany widok DAB nie mają pierwszeństwa przed świeżym, widocznym FM. Rzeczywista sesja DAB `PLAYING` zachowuje dotychczasowy priorytet — nie ustalamy stanu sprzętowego audio na podstawie samego otwarcia ekranu.
- Wybrany w aplikacji tryb zgodności DAB jest zachowany: natywny DAB korzysta z `SOURCE:DAB` i `DAB:TEXT`, tryb starszego ESP z `SOURCE:MP3` i `TITLE`.
- Diagnostyka osobno pokazuje ustawienie Dostępności, połączenie usługi i czas ostatniego poprawnego cyklu odczytu. Dopasowuje zarówno pełną, jak i skróconą nazwę komponentu Androida.
- Ostatnie 12 zdarzeń Dostępności/powiadomień/startu/wybudzenia jest przechowywane również po zamknięciu procesu. Znaczniki czasu obejmują czas systemowy i uptime; nie są pełnym logiem systemu Android i nie wyjaśniają samodzielnie, dlaczego system wycofał uprawnienie.
- Przy rozłączeniu dostępu do powiadomień aplikacja prosi Androida o ponowne połączenie wyłącznie wtedy, gdy użytkownik nadal ma włączony dostęp (maksymalnie co 30 sekund, Android 7+). Nie nadaje uprawnień automatycznie i nie zmienia ustawień Dostępności.
- Działający most reaguje na SCREEN_ON, USER_PRESENT oraz zmianę stanu Bluetooth. Sam odbiornik dynamiczny nie uruchomi procesu zabitego przez firmware; dotychczasowy odbiornik BOOT_COMPLETED i systemowe usługi pozostają potrzebne.

## APK i podpis

Workflow buduje testy oraz APK jako artefakt GitHub Actions; niczego nie publikuje w Releases i nie nadpisuje starszych wydań. APK nadal używa `pl.krupapiotr.mediaprobe`. Podpis debug z tego kompilowania może różnić się od podpisu zainstalowanego APK. Jeśli aktualizacja jest odrzucana z powodu podpisu, nie odinstalowuj działającej wersji tylko po to, aby przeprowadzić ten test. Potrzebny jest zgodny klucz podpisujący lub świadomie przygotowany osobny wariant testowy.

## Test na postoju

1. Po aktualizacji sprawdź wersję `1.3.7-source-access-k706-test`. Zachowaj dotychczasowy wybór trybu DAB. Nowe komunikaty źródła są obsługiwane przez dostarczony ESP 1.9x21.
2. Przełącz DAB+ → FM: sprawdź zmianę `R+|` na `R|`, tekst stacji/RDS oraz zachowanie przed nadejściem RDS. Sprawdź też FM → DAB, MP3 ↔ YouTube oraz koniec rozmowy.
3. Przed wyłączeniem radia naciśnij **Kopiuj** i zachowaj diagnostykę.
4. Wyłącz/włącz radio. Zanim ponownie nadasz uprawnienia, otwórz aplikację i naciśnij **Kopiuj**. Otworzenie aplikacji samo uruchamia most i może próbować ponownie połączyć powiadomienia, ale nie włącza Dostępności.
5. `WYŁĄCZONE W SYSTEMIE` oznacza brak aktualnego ustawienia dostępu. `WŁĄCZONE / USŁUGA NIEPOŁĄCZONA` oznacza zachowany dostęp bez działającego połączenia usługi. `USŁUGA POŁĄCZONA` i rosnący czas ostatniego odczytu wskazuje na problem cyklu odczytu. Błąd odczytu jest pokazany osobno.
6. Zachowaj diagnostykę przed ręcznym włączeniem dostępu i po nim. Na jej podstawie można wybrać dalszą naprawę K706. Ta wersja nie jest potwierdzoną naprawą cofania uprawnień przez firmware radia.

## Walidacja

JUnit obejmuje przejście DAB → FM bez RDS, MP3 → YouTube → OFF, brak powtarzanego kasowania stabilnego źródła, odtworzenie stanu po reconnect/rozmowie, priorytet powiadomień DAB i dokładne rozpoznawanie komponentów uprawnień. Workflow wykonuje też dotychczasowe testy projektu, kompilację Android i weryfikację podpisu APK. Testy nie zastępują cyklu zasilania na fizycznym K706.

## ESP 1.9x22-EMPTY-REFRESH

Dołączony szkic `firmware/Krupas_1_9x22_EMPTY_REFRESH/` pochodzi bezpośrednio z przesłanego kodu 1.9x21. Zmieniono wyłącznie możliwość wysłania pustego okna oraz zachowanie oczekującego kasowania do chwili udanej transmisji. Dzięki temu CLEAR:ALL może skasować ekran także bez odczytu temperatury. Payload, CAN/ISO-TP i parser źródeł pozostały takie same. Test hosta C++ sprawdził puste okno, nieudaną transmisję i ponowienie, granice źródeł DAB → FM / MP3 → YT i priorytet rozmowy. Nie wykonano kompilacji Arduino ani testu fizycznego wyświetlacza. Do testu samego wyboru FM najpierw można użyć aplikacji z dotychczasowym ESP; szkic jest potrzebny do pełnego testu pustego ekranu/STOP bez temperatury.

Lokalnie przeszło 28 testów JUnit (istniejące i nowe) oraz powyższy test logiki ESP w C++. Wynik kompilacji APK należy sprawdzić w GitHub Actions dla konkretnego commitu.
