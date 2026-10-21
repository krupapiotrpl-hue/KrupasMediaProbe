# 1.9D COOLANT DIAG — etap diagnostyczny

To osobny szkic na bazie zachowanego firmware 1.9D dla LilyGO T-2CAN / ESP32-S3, CAN A. Zachowuje obsługę BLE i napisów wersji bazowej. Nie wpisuje temperatury do dolnego paska CID: adresowanie tego pola nie jest jeszcze potwierdzone.

Cel: sprawdzić, czy CID w Zafirze B 2005 1.8 udostępnia temperaturę płynu przez MS-CAN. Ręczna klimatyzacja nie gwarantuje dostępności wolnego pola do zapisu.

Po otwarciu szkicu w Arduino IDE użyj swoich dotychczasowych ustawień płytki i portu. Monitor portu szeregowego: 115200 baud. Sprawdzenie na postoju. Po uruchomieniu moduł temperatury nie wysyła automatycznych zapytań. Polecenie T wysyła jedno zapytanie odczytu do DIS; R uruchamia maksymalnie 30 zapytań w odstępach co najmniej 2 s; X zatrzymuje zapytania; S pokazuje stan. Po restarcie wraca do trybu bez automatycznych zapytań.

Odczyt jest oznaczony jako wstępny. Brak odpowiedzi, błędna ramka lub odczyt starszy niż 5 s nie są temperaturą 0 C. Porównaj otrzymaną wartość z odczytem diagnostycznym auta, zanim zostanie użyta jako wskazanie temperatury na ekranie. Nie zmienia sterowania silnikiem, wentylatorem, zegarem ani temperaturą zewnętrzną; nie zawiera niepotwierdzonych poleceń zapisu stopki.

Prześlij zapis monitora od momentu uruchomienia oraz po poleceniu T/R. Sama udana kompilacja nie potwierdza działania w aucie. Powrót do oryginalnego 1.9D: wgraj zachowany oryginalny szkic.

Źródła protokołu:
- https://github.com/PNKP237/EHU32/blob/main/src/CAN.ino (odczyt DIS 0x246 / 0x546, blok 0x0B)
- https://github.com/PNKP237/EHU32/wiki/Programming-for-Opel-Vauxhall-displays-and-general-GM-CAN%E2%80%90bus-quirks
- https://github.com/JJToB/Car-CAN-Message-DB/blob/master/Opel/Astra/H/MS-CAN/Info-Display.md (pola audio/klimatyzacji; nie potwierdza wskazanego położenia w CID)
