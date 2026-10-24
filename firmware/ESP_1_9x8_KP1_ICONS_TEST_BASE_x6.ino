/*
 KRUPAS OPEL DISPLAY 1.9x8 — BLE ICON PROTOCOL COMPATIBILITY TEST
 Data: 2026-10-24 | PROTOTYP TESTOWY, NIE WGRYWAC ZAMIAST 1.9x7!
 Baza techniczna: plik 1.9x6 RDS NORMAL FONT TEST (ponieważ kod 1.9x7
 nie jest dostępny w repozytorium ani w Bibliotece plików).
 Nowe: odczyt opcjonalnego protokołu @KP1|SRC / @KP1|CALL,
       charakterystyka BLE tylko do odczytu z możliwościami protokołu,
       zgodność ze starymi tytułami i komendami SRC:/CALL:.
 Zachowano istniejące mechanizmy CAN A / OEM, tekstu 0x6C, pól 0x11/0x12,
 temperatury i scrolla. Symbole CID są NIEZWERYFIKOWANE W AUCIE.
 UWAGA: APK 0.6.7 nie wysyła @KP1. Aby w pełni przetestować symbole
 YouTube, MP3 i połączeń potrzebna jest osobna zgodna APK z negocjacją.
*/
/*
 KRUPAS OPEL DISPLAY 1.9x6 — RDS NORMAL FONT TEST
 Data: 2026-10-23 | STATUS: TESTOWY
 Baza: 1.9x5 FONT SOURCE TEST
 Zmiana 1.9x6: dla pola RDS zmieniono sekwencję formatowania
 ESC [ f S _ g m (pogrubiona) na ESC [ f S _ d m (normalna).
 Konkretnie: 0x00,0x67 -> 0x00,0x64 we wzorcu buildPayload().
 Bez zmian w logice CAN, BLE, temperatury płynu, ikon i scrolla.
 UWAGA: normalna czcionka może być wizualnie węższa/cieńsza,
 ale rzeczywista wysokość fontu nie jest osobno regulowana.
 Wersja nie przeszła jeszcze kompilacji Arduino ani testu w aucie.
*/
/*
 KRUPAS OPEL DISPLAY 1.9x6 - RDS NORMAL FONT TEST
 2026-10-23 | TEST | BAZA: 1.9x3 ICONS CALLS (poprawiona deklaracja)
 1.9x5: rozpoznawanie Radio/FM, BT, MP3, YouTube/YT z prefiksow BLE;
         usuniecie prefiksu z tytulu i stala 1-pozycyjna ikonka;
         poprawiony warunek przewijania przy szerokosci 11 znakow;
         logi diagnostyczne [SOURCE] i [BLE RAW].
         RDS: styl tekstu bez ingerencji w nieznane bajty OEM.
         Mniejsza czcionka nadal NIEPOTWIERDZONA; wymaga mapy stylow CID.
 1.9x4: poprawka deklaracji coolantDisplayAvailable w równoległej bazie.
 1.9x3: protokol SRC:/CALL:, stałe symbole i tekst przewijany.
 1.9x2: historia i identyfikacja; 1.9x: AUTO RECOVERY, temp CAN.
 UWAGA: źródło anonimowego tytułu nie jest możliwe do wiarygodnego
 wykrycia na ESP; wtedy zachowujemy tryb 12 znakow bez ikony.
 APK musi przeslac SRC:YT|tytul albo prefiks YT:/YouTube: itd.
 ZNAKI Unicode sa testowe, CID moze ich nie obslugiwac.
 Nie zmieniać CAN ani uruchamiać testów podczas jazdy.
*/
/*
 KRUPAS OPEL DISPLAY 1.9x3 ICONS + CALLS | 2026-10-23 | TEST
 BAZA: 1.9x2 AUTO RECOVERY; oryginalu nie nadpisywac.
 W 1.9x3:
  - komendy BLE SRC:RADIO|tekst, SRC:BT|tekst, SRC:MP3|tekst,
    SRC:YT|tekst (takze FM / YOUTUBE) i CALL:NAZWA, CALL_END.
  - jednopozycyjny, nieruchomy symbol + 11 znakow scrolla.
  - priorytet rozmowy i powrot do zapamietanego odtwarzania.
  - tekst BLE bez prefiksu dziala jak dotychczas (12 znakow).
  - symbole sa probnymi kodami Unicode: CID moze ich nie rysowac.
    Kod 0x25A3 nie jest ikona radia; to tymczasowy znacznik testowy.
  - ESP NIE ODCZYTUJE kontaktow ani źródła z Androida: APK musi
    wyslac opisane powyzej zdarzenia. Bez nich nie bedzie automatyki.
  - OEM i pasywny odczyt temperatury pozostaja bez zmian.
  - zachowano odswiezanie co 2 s, ponawianie CAN co 350 ms,
    przewijanie dlugich tytulow i wysylanie plynu bez tytulu BLE.
  - poprawiono kolejnosc deklaracji coolantDisplayAvailable (kompilacja).
 STATUS: NIETESTOWANY na sprzęcie; brak potwierdzenia kompilacji.
 Poprzednie wersje: 1.9x2 historia i monitor, 1.9x recovery.
*/
/*
================================================================
KRUPAS OPEL DISPLAY — HISTORIA WERSJI
================================================================
WERSJA ARCHIWALNA: 1.9x2 AUTO RECOVERY (historia poprzedniej)
DATA: 2026-10-23
STATUS: TESTOWA — niekompilowana i niesprawdzona w samochodzie
BAZA: 1.9x AUTO RECOVERY (wg opisu: oparta na 1.9w)

ZMIANY 1.9x2 W STOSUNKU DO 1.9x:
- Dodano ten dziennik zmian i jednoznaczne oznaczenie wersji.
- Ujednolicono nazwę wyświetlaną w monitorze szeregowym.
- Logika BLE, CAN, odświeżania i wyświetlacza BEZ ZMIAN.

FUNKCJE ODZIEDZICZONE Z 1.9x (nie są nowymi zmianami x2):
- Odświeżanie krótkich napisów co 2 sekundy.
- Ponawianie nieudanych transmisji CAN.
- Próba odświeżania temperatury również bez tytułów BLE.
- Liczniki diagnostyczne BLE/CAN i zachowanie przewijania.
- Oryginalne komunikaty Opla pozostają w istniejącej obsłudze.

ZASADY:
- Nie wykorzystywać ponownie raz nadanego numeru.
- Kolejne wersje dopisywać w tym dzienniku, najnowszą na górze.
- Nie nadpisywać wcześniej działających szkiców.
- Każdy test opisać oddzielnie; nie oznaczać jako „sprawdzone”
  przed rzeczywistą próbą w samochodzie.
================================================================
*/

// KRUPAS OPEL DISPLAY v1.9x - AUTO RECOVERY; based on v1.9w.
// OEM transport untouched, physical CID layout unverified.
// KRUPAS OPEL DISPLAY v1.9r - test ukladu 3 pol CID.
// OEM (linia 1) bez wlasnego tekstu; ID10 = RDS; ID11 = puste; ID12 = PLYN.
// UWAGA: ID10/11/12 to pola audio, nie udowodnione fizyczne linie ekranu.
// Komunikaty OEM moga nadal nadpisac caly obszar. Testowac na postoju.
// Bez zapytan o predkosc i bez ingerencji w 0x180/0x683/0x6C8.

/*

  ============================================================

  KRUPAS OPEL DISPLAY v1.9r THREE AUDIO FIELDS

  ============================================================



  OSTATNIA DZIAŁAJĄCA BAZA:

    v1.9c



  POTWIERDZONA POPRAWKA:

    0x63 -> 0x6C

    tekst wyrównany do lewej



  FUNKCJE:

    - BLE -> CAN A

    - UTF-8

    - polskie znaki

    - scroll długiego TITLE

    - ten sam TITLE NIE resetuje scrolla

    - krótki TITLE: podtrzymanie 700 ms

    - CAN A ONLY



  LilyGO T-2CAN / ESP32-S3



  CAN A / MCP2515:

    CS   = GPIO10

    SCLK = GPIO12

    MOSI = GPIO11

    MISO = GPIO13

    RST  = GPIO9

    INT  = GPIO8



  Opel MS-CAN:

    95.238 kb/s



  DISPLAY:

    TX = 0x6C1

    FLOW CONTROL = 0x2C1



  BLE:

    ZAFIRA-DISPLAY



    SERVICE:

      6fbd0001-7c40-4a16-93c5-6c0c70696f74



    WRITE:

      6fbd0002-7c40-4a16-93c5-6c0c70696f74

  ============================================================

*/



#include <Arduino.h>

#include <SPI.h>



#include <BLEDevice.h>

#include <BLEServer.h>

#include <BLEUtils.h>





// ============================================================

// CAN A

// ============================================================



#define CAN_CS    10

#define CAN_SCLK  12

#define CAN_MOSI  11

#define CAN_MISO  13

#define CAN_RST   9

#define CAN_INT   8





// ============================================================

// BLE

// ============================================================



static const char *BLE_NAME =

  "ZAFIRA-DISPLAY";



static const char *SERVICE_UUID =

  "6fbd0001-7c40-4a16-93c5-6c0c70696f74";



static const char *CHAR_UUID =

  "6fbd0002-7c40-4a16-93c5-6c0c70696f74";

// Tylko do odczytu: nowa APK może bezpiecznie wykryć obsługę KP1.
static const char *CAP_UUID = "6fbd0003-7c40-4a16-93c5-6c0c70696f74";





BLEServer *bleServer = nullptr;

BLECharacteristic *bleCharacteristic = nullptr;



bool bleConnected = false;





// ============================================================

// BLE RX

// ============================================================



String pendingText = "";



volatile bool newTextAvailable = false;



portMUX_TYPE textMux =

  portMUX_INITIALIZER_UNLOCKED;





// ============================================================

// TITLE

// ============================================================



const uint16_t MAX_TITLE_CHARS = 96;



uint16_t titleChars[MAX_TITLE_CHARS];



uint16_t titleLength = 0;



String lastTitleText = "";





// ============================================================

// DISPLAY

// ============================================================



const uint8_t DISPLAY_CHARS = 12;



const uint32_t SCROLL_INTERVAL_MS = 220;



const uint32_t REFRESH_INTERVAL_MS = 2000;



const uint32_t RETRY_INTERVAL_MS = 350;



const uint32_t FLOW_CONTROL_TIMEOUT_MS = 150;





uint16_t scrollPosition = 0;



uint32_t lastSuccessfulTx = 0;



uint32_t lastAttempt = 0;



uint32_t lastScroll = 0;



bool lastTxWasSuccessful = false;
uint32_t txSuccessCount = 0;
uint32_t txFailureCount = 0;
uint32_t lastDiagnosticMs = 0;






// ============================================================

// TEMPERATURA PŁYNU (MS-CAN 0x4EC)
// 0x4EC: dane [0]=0x46, [2]=T+40, długość 5.
// Źródło protokołu: PL125 / Opel_Astra_H_MsCan.ino.
//
// UWAGA: DOCELOWA POZYCJA NA DOLNYM PASKU GID/CID NIE JEST
// JESZCZE POTWIERDZONA. Nie modyfikujemy fabrycznego zegara,
// temperatury zewnętrznej ani ramek klimatyzacji.
// WERSJA TESTOWA pokazuje płyn NA POLU RDS przez 5 s / 30 s;
// gdy nie ma tekstu BLE, pokazuje pomiar nieprzerwanie.
// Brak ramek => brak zgadywanych wartości. Odczyt pasywny.
// ============================================================

const uint16_t COOLANT_FRAME_ID = 0x4EC;
const uint32_t COOLANT_STALE_MS = 15000;
const uint32_t COOLANT_CYCLE_MS = 30000;
const uint32_t COOLANT_ON_MS = 5000;

bool coolantValid = false;
int16_t coolantC = 0;
uint32_t coolantLastFrameMs = 0;
uint32_t coolantCycleStartMs = 0;
bool coolantOverlayWasActive = false;

// Wyswietlamy PLYN nawet bez tytulu z Androida.
bool coolantDisplayAvailable() {
  return coolantValid && (millis() - coolantLastFrameMs <= COOLANT_STALE_MS);
}


bool showCoolantOnRds() {
  // v1.9p: RDS stays exclusively for media title. No overlay.
  return false;
}

void processCoolantFrame(uint16_t id, const uint8_t *data, uint8_t len) {
  if (id != COOLANT_FRAME_ID || len != 5 || data[0] != 0x46) {
    return;
  }
  const int16_t measured = (int16_t)data[2] - 40;
  if (measured < -40 || measured > 140) {
    return;
  }
  const bool changed = !coolantValid || coolantC != measured;
  if (!coolantValid) {
    coolantCycleStartMs = millis();
  }
  coolantC = measured;
  coolantValid = true;
  coolantLastFrameMs = millis();
  if (changed) {
    Serial.print("[TEMP] PLYN: ");
    Serial.print(coolantC);
    Serial.println(" C (CAN 0x4EC)");
  }
}

// Używamy JEDYNEGO istniejącego readCAN() oraz CAN A.
// Bez dodatkowego kontrolera, bez OBD request i bez ingerencji
// w ramki fabryczne. Nie odczytujemy CAN w trakcie ISO-TP TX.
bool readCAN(uint16_t &id, uint8_t *data, uint8_t &len);


void pollCoolantCAN() {
  for (uint8_t i = 0; i < 12; ++i) {
    uint16_t id = 0;
    uint8_t data[8] = {0};
    uint8_t len = 0;
    if (!readCAN(id, data, len)) {
      break;
    }
    processCoolantFrame(id, data, len);

  }
}

// ============================================================
// v1.9r: usunieto sonde predkosci i jej zapytania CAN.


// ============================================================

// MCP2515

// ============================================================



#define MCP_RESET       0xC0

#define MCP_READ        0x03

#define MCP_WRITE       0x02

#define MCP_BITMOD      0x05



#define CANSTAT         0x0E

#define CANCTRL         0x0F



#define CNF3            0x28

#define CNF2            0x29

#define CNF1            0x2A



#define CANINTF         0x2C



#define RXB0CTRL        0x60

#define RXB0SIDH        0x61

#define RXB0SIDL        0x62

#define RXB0DLC         0x65

#define RXB0D0          0x66



#define RXB1CTRL        0x70

#define RXB1SIDH        0x71

#define RXB1SIDL        0x72

#define RXB1DLC         0x75

#define RXB1D0          0x76



#define TXB0CTRL        0x30

#define TXB0SIDH        0x31

#define TXB0SIDL        0x32

#define TXB0DLC         0x35

#define TXB0D0          0x36





// ============================================================

// MCP SPI

// ============================================================



void mcpSelect()

{

  digitalWrite(CAN_CS, LOW);

}





void mcpDeselect()

{

  digitalWrite(CAN_CS, HIGH);

}





void mcpReset()

{

  SPI.beginTransaction(

    SPISettings(

      8000000,

      MSBFIRST,

      SPI_MODE0

    )

  );



  mcpSelect();



  SPI.transfer(MCP_RESET);



  mcpDeselect();



  SPI.endTransaction();



  delay(20);

}





void mcpWriteRegister(

  uint8_t address,

  uint8_t value

)

{

  SPI.beginTransaction(

    SPISettings(

      8000000,

      MSBFIRST,

      SPI_MODE0

    )

  );



  mcpSelect();



  SPI.transfer(MCP_WRITE);

  SPI.transfer(address);

  SPI.transfer(value);



  mcpDeselect();



  SPI.endTransaction();

}





uint8_t mcpReadRegister(

  uint8_t address

)

{

  SPI.beginTransaction(

    SPISettings(

      8000000,

      MSBFIRST,

      SPI_MODE0

    )

  );



  mcpSelect();



  SPI.transfer(MCP_READ);

  SPI.transfer(address);



  uint8_t value =

    SPI.transfer(0x00);



  mcpDeselect();



  SPI.endTransaction();



  return value;

}





void mcpBitModify(

  uint8_t address,

  uint8_t mask,

  uint8_t data

)

{

  SPI.beginTransaction(

    SPISettings(

      8000000,

      MSBFIRST,

      SPI_MODE0

    )

  );



  mcpSelect();



  SPI.transfer(MCP_BITMOD);

  SPI.transfer(address);

  SPI.transfer(mask);

  SPI.transfer(data);



  mcpDeselect();



  SPI.endTransaction();

}





// ============================================================

// INIT CAN

// ============================================================



bool initCAN()

{

  pinMode(CAN_CS, OUTPUT);

  digitalWrite(CAN_CS, HIGH);



  pinMode(CAN_RST, OUTPUT);

  digitalWrite(CAN_RST, HIGH);



  pinMode(CAN_INT, INPUT_PULLUP);





  SPI.begin(

    CAN_SCLK,

    CAN_MISO,

    CAN_MOSI,

    CAN_CS

  );





  digitalWrite(CAN_RST, LOW);

  delay(10);



  digitalWrite(CAN_RST, HIGH);

  delay(20);





  mcpReset();





  // CONFIG MODE



  mcpWriteRegister(

    CANCTRL,

    0x80

  );



  delay(10);





  // Opel MS-CAN 95.238 kb/s



  mcpWriteRegister(

    CNF1,

    0x03

  );



  mcpWriteRegister(

    CNF2,

    0xAD

  );



  mcpWriteRegister(

    CNF3,

    0x07

  );





  // Wszystkie standardowe ramki



  mcpWriteRegister(

    RXB0CTRL,

    0x60

  );



  mcpWriteRegister(

    RXB1CTRL,

    0x60

  );





  mcpWriteRegister(

    CANINTF,

    0x00

  );





  // NORMAL MODE



  mcpWriteRegister(

    CANCTRL,

    0x00

  );



  delay(20);





  uint8_t stat =

    mcpReadRegister(

      CANSTAT

    );





  if (

    (stat & 0xE0) != 0x00

  )

  {
return false;

  }





  return true;

}





// ============================================================

// SEND CAN

// ============================================================


bool sendCAN(

  uint16_t id,

  const uint8_t *data,

  uint8_t len

)

{

  if (

    len > 8

  )

  {

    len = 8;

  }





  uint32_t start =

    millis();





  while (

    mcpReadRegister(

      TXB0CTRL

    ) & 0x08

  )

  {

    if (

      millis() - start > 50

    )

    {

      return false;

    }



    delay(1);

  }





  uint8_t sidh =

    (id >> 3) & 0xFF;





  uint8_t sidl =

    (id & 0x07) << 5;





  mcpWriteRegister(

    TXB0SIDH,

    sidh

  );





  mcpWriteRegister(

    TXB0SIDL,

    sidl

  );





  mcpWriteRegister(

    TXB0DLC,

    len & 0x0F

  );





  for (

    uint8_t i = 0;

    i < len;

    i++

  )

  {

    mcpWriteRegister(

      TXB0D0 + i,

      data[i]

    );

  }





  mcpBitModify(

    TXB0CTRL,

    0x08,

    0x08

  );





  start =

    millis();





  while (

    mcpReadRegister(

      TXB0CTRL

    ) & 0x08

  )

  {

    if (

      millis() - start > 50

    )

    {

      return false;

    }



    delay(1);

  }





  return true;

}





// ============================================================

// READ CAN

// ============================================================



bool readCAN(

  uint16_t &id,

  uint8_t *data,

  uint8_t &len

)

{

  uint8_t flags =

    mcpReadRegister(

      CANINTF

    );





  uint8_t baseSIDH;

  uint8_t baseSIDL;

  uint8_t baseDLC;

  uint8_t baseDATA;

  uint8_t clearMask;





  if (

    flags & 0x01

  )

  {

    baseSIDH =

      RXB0SIDH;



    baseSIDL =

      RXB0SIDL;



    baseDLC =

      RXB0DLC;



    baseDATA =

      RXB0D0;



    clearMask =

      0x01;

  }



  else if (

    flags & 0x02

  )

  {

    baseSIDH =

      RXB1SIDH;



    baseSIDL =

      RXB1SIDL;



    baseDLC =

      RXB1DLC;



    baseDATA =

      RXB1D0;



    clearMask =

      0x02;

  }



  else

  {

    return false;

  }





  uint8_t sidh =

    mcpReadRegister(

      baseSIDH

    );





  uint8_t sidl =

    mcpReadRegister(

      baseSIDL

    );





  id =

    ((uint16_t)sidh << 3)

    |

    ((sidl >> 5) & 0x07);





  len =

    mcpReadRegister(

      baseDLC

    ) & 0x0F;





  if (

    len > 8

  )

  {

    len = 8;

  }





  for (

    uint8_t i = 0;

    i < len;

    i++

  )

  {

    data[i] =

      mcpReadRegister(

        baseDATA + i

      );

  }





  mcpBitModify(

    CANINTF,

    clearMask,

    0x00

  );





  return true;

}





// ============================================================

// FLOW CONTROL

// ============================================================



bool waitFlowControl(

  uint8_t &blockSize,

  uint32_t &stMinMs

)

{

  uint32_t start =

    millis();





  while (

    millis() - start <

    FLOW_CONTROL_TIMEOUT_MS

  )

  {

    uint16_t id;



    uint8_t data[8];



    uint8_t len;





    if (

      !readCAN(

        id,

        data,

        len

      )

    )

    {

      delay(1);



      continue;

    }





    // W tej pętli normalnie pomijaliśmy wszystko poza 0x2C1.
    // Teraz przechwytujemy także ramkę temperatury, ale nie
    // zakłócamy oczekiwania na Flow Control 0x2C1.
    processCoolantFrame(id, data, len);


    if (

      id != 0x2C1

    )

    {

      continue;

    }





    if (

      len < 3

    )

    {

      continue;

    }





    if (

      (data[0] & 0xF0) !=

      0x30

    )

    {

      continue;

    }





    uint8_t flowStatus =

      data[0] & 0x0F;





    if (

      flowStatus == 0x00

    )

    {

      blockSize =

        data[1];





      uint8_t st =

        data[2];





      if (

        st <= 0x7F

      )

      {

        stMinMs =

          st;

      }



      else

      {

        stMinMs =

          1;

      }





      if (

        stMinMs < 4

      )

      {

        stMinMs =

          4;

      }





      return true;

    }





    if (

      flowStatus == 0x02

    )

    {

      return false;

    }

  }





  return false;

}





// ============================================================

// UTF-8 -> UNICODE

// ============================================================



uint16_t decodeUtf8(

  const String &text,

  uint16_t *output,

  uint16_t maxChars

)

{

  uint16_t outCount =

    0;





  size_t i =

    0;





  while (

    i < text.length() &&

    outCount < maxChars

  )

  {

    uint8_t c =

      (uint8_t)text[i];





    uint32_t codePoint =

      0;





    if (

      c < 0x80

    )

    {

      codePoint =

        c;



      i++;

    }



    else if (

      (c & 0xE0) == 0xC0 &&

      i + 1 < text.length()

    )

    {

      uint8_t c2 =

        (uint8_t)text[i + 1];





      if (

        (c2 & 0xC0) ==

        0x80

      )

      {

        codePoint =

          ((uint32_t)(c & 0x1F) << 6)

          |

          (uint32_t)(c2 & 0x3F);



        i += 2;

      }



      else

      {

        codePoint =

          '?';



        i++;

      }

    }



    else if (

      (c & 0xF0) == 0xE0 &&

      i + 2 < text.length()

    )

    {

      uint8_t c2 =

        (uint8_t)text[i + 1];





      uint8_t c3 =

        (uint8_t)text[i + 2];





      if (

        (c2 & 0xC0) == 0x80 &&

        (c3 & 0xC0) == 0x80

      )

      {

        codePoint =

          ((uint32_t)(c & 0x0F) << 12)

          |

          ((uint32_t)(c2 & 0x3F) << 6)

          |

          (uint32_t)(c3 & 0x3F);



        i += 3;

      }



      else

      {

        codePoint =

          '?';



        i++;

      }

    }



    else if (

      (c & 0xF8) == 0xF0 &&

      i + 3 < text.length()

    )

    {

      codePoint =

        '?';



      i += 4;

    }



    else

    {

      codePoint =

        '?';



      i++;

    }





    if (

      codePoint < 32

    )

    {

      if (

        codePoint == '\n' ||

        codePoint == '\r' ||

        codePoint == '\t'

      )

      {

        codePoint =

          ' ';

      }



      else
      {

        continue;

      }

    }





    if (

      codePoint <= 0xFFFF

    )

    {

      output[outCount++] =

        (uint16_t)codePoint;

    }



    else

    {

      output[outCount++] =

        '?';

    }

  }





  while (

    outCount > 0 &&

    output[outCount - 1] == ' '

  )

  {

    outCount--;

  }





  return outCount;

}





// ============================================================

// SERIAL UTF-8

// ============================================================



void printUnicodeChar(

  uint16_t ch

)

{

  if (

    ch < 0x80

  )

  {

    Serial.write(

      (uint8_t)ch

    );



    return;

  }





  if (

    ch < 0x800

  )

  {

    Serial.write(

      0xC0 |

      (ch >> 6)

    );





    Serial.write(

      0x80 |

      (ch & 0x3F)

    );





    return;

  }





  Serial.write(

    0xE0 |

    (ch >> 12)

  );





  Serial.write(

    0x80 |

    ((ch >> 6) & 0x3F)

  );





  Serial.write(

    0x80 |

    (ch & 0x3F)

  );

}





// ============================================================

// OKNO 12 ZNAKÓW

// ============================================================



// v1.9w TEST: pelne okno 12 znakow na RDS, bez stalego prefiksu RAD/FM.
// Nie modyfikujemy mapowania 0x11/0x12, bo fizyczne wiersze CID
// nie sa jeszcze potwierdzone. Oryginalne komunikaty Opla pozostaja nietkniete.
// Obslugiwane zrodla wymagaja jawnego prefiksu od APK po BLE.
enum KrupasSource : uint8_t { SRC_LEGACY, SRC_RADIO, SRC_BT, SRC_MP3, SRC_YT, SRC_NAV };
KrupasSource activeSource = SRC_LEGACY;
KrupasSource savedSource = SRC_LEGACY;
String savedMediaText = "";
bool callActive = false;
// UWAGA: numery Unicode sa PROBNE; brak pewnosci, czy CID je rysuje.
// Nie wysylamy emoji - nie mieszcza sie w 16-bitowym polu.
uint16_t sourceGlyph() {
  switch (activeSource) {
    case SRC_RADIO: return 0x25A3; // symbol odbiornika - test czcionki CID
    case SRC_BT:    return 0x260E; // symbol telefonu - test czcionki CID
    case SRC_MP3:   return 0x266A; // nutka
    case SRC_YT:    return 0x25B6; // play
    case SRC_NAV:   return 0x2192; // strzalka - test fontu CID
    default:        return ' ';
  }
}
uint8_t mediaWidth() { return activeSource == SRC_LEGACY ? DISPLAY_CHARS : DISPLAY_CHARS-1; }
static const uint8_t MEDIA_WIDTH = DISPLAY_CHARS; // zachowanie kodu bazowego; uzywaj mediaWidth() dla scrolla.

void getDisplayWindow(uint16_t *window) {
  for (uint8_t i = 0; i < DISPLAY_CHARS; ++i) window[i] = ' ';
  const uint8_t offset = activeSource == SRC_LEGACY ? 0 : 1;
  if (offset) window[0] = sourceGlyph();
  if (!titleLength) return;
  const uint8_t width = mediaWidth();
  const uint16_t virtualLength = titleLength > width ? titleLength + 3 : titleLength;
  for (uint8_t i = 0; i < width; ++i) {
    const uint16_t idx = titleLength <= width ? i : (scrollPosition + i) % virtualLength;
    if (idx < titleLength) window[offset+i] = titleChars[idx];
  }
}

// ============================================================

// PAYLOAD OPLA

// ============================================================



void buildPayload(

  const uint16_t *window,

  uint8_t *payload

)

{

  /*

    ==========================================================

    POTWIERDZONE U CIEBIE:



    BYŁO:

      0x63



    ZMIANA:

      0x6C



    Po tej zmianie tekst przeszedł

    z wyśrodkowanego na lewą stronę.

    ==========================================================

  */





  const uint8_t prefix[28] =

  {

    0xC0, 0x00,

    0x31, 0x03,

    0x10, 0x17,



    0x00, 0x1B,

    0x00, 0x5B,



    // LEFT - potwierdzone

    0x00, 0x6C,



    0x00, 0x6D,



    0x00, 0x1B,

    0x00, 0x5B,

    0x00, 0x66,

    0x00, 0x53,

    0x00, 0x5F,

    0x00, 0x64, // FONT NORMAL (cienki), poprzednio 0x67 = BOLD

    0x00, 0x6D

  };





  memcpy(

    payload,

    prefix,

    sizeof(prefix)

  );





  for (

    uint8_t i = 0;

    i < DISPLAY_CHARS;

    i++

  )

  {

    uint16_t ch =

      window[i];





    payload[

      28 + i * 2

    ] =

      (ch >> 8) & 0xFF;





    payload[

      29 + i * 2

    ] =

      ch & 0xFF;

  }

}





// ============================================================

// ISO-TP TX

// ============================================================



bool sendDisplayWindow(const uint16_t *window)
{
  // v1.9s TEST: pierwszego pola audio 0x10 nie wysylamy.
  // 0x11 = multimedia, 0x12 = temperatura plynu.
  // Numery pol NIE gwarantuja fizycznej kolejnosci wierszy CID.
  uint8_t payload[78] = {0};
  const uint8_t payloadLength = sizeof(payload);
  buildPayload(window, payload);
  payload[2] = payloadLength - 3;
  payload[4] = 0x11;
  payload[52] = 0x12;
  payload[53] = 0x0C;
  uint16_t third[12];
  for (uint8_t i = 0; i < 12; ++i) third[i] = ' ';
  if (coolantValid && millis() - coolantLastFrameMs <= COOLANT_STALE_MS) {
    const uint16_t prefix[] = {'P', 0x0141, 'Y', 'N', ' '};
    for (uint8_t i=0; i<5; ++i) third[i]=prefix[i];
    char number[7];
    snprintf(number, sizeof(number), "%d", (int)coolantC);
    uint8_t pos=5;
    for (uint8_t i=0; number[i] && pos<10; ++i) third[pos++]=(uint8_t)number[i];
    if (pos<12) third[pos++]=0x00B0;
    if (pos<12) third[pos++]='C';
  }
  for (uint8_t i=0; i<12; ++i) {
    payload[54+2*i]=(uint8_t)(third[i]>>8);
    payload[55+2*i]=(uint8_t)(third[i]&0xFF);
  }

  // ----------------------------------------------------------

  // FIRST FRAME

  // ----------------------------------------------------------



  uint8_t ff[8];





  ff[0] =

    0x10;





  ff[1] =

    payloadLength;





  for (

    uint8_t i = 0;

    i < 6;

    i++

  )

  {

    ff[

      2 + i

    ] =

      payload[i];

  }





  if (

    !sendCAN(

      0x6C1,

      ff,

      8

    )

  )

  {

    return false;

  }





  // ----------------------------------------------------------

  // FLOW CONTROL

  // ----------------------------------------------------------



  uint8_t blockSize =

    0;





  uint32_t stMinMs =

    4;





  if (

    !waitFlowControl(

      blockSize,

      stMinMs

    )

  )

  {

    return false;

  }





  // ----------------------------------------------------------

  // CONSECUTIVE FRAMES

  // ----------------------------------------------------------



  uint8_t sequence =

    1;





  uint8_t offset =

    6;





  uint8_t framesInBlock =

    0;





  while (

    offset <

    payloadLength

  )

  {

    uint8_t cf[8];





    memset(

      cf,

      0,

      sizeof(cf)

    );





    cf[0] =

      0x20 |

      (sequence & 0x0F);





    uint8_t count =

      payloadLength - offset;





    if (

      count > 7

    )

    {

      count =

        7;

    }





    for (

      uint8_t i = 0;

      i < count;

      i++

    )

    {

      cf[

        1 + i

      ] =

        payload[

          offset + i

        ];

    }





    if (

      !sendCAN(

        0x6C1,

        cf,

        8

      )

    )

    {

      return false;

    }





    offset +=

      count;





    sequence++;





    if (

      sequence > 15

    )

    {

      sequence =

        0;

    }





    framesInBlock++;





    delay(

      stMinMs

    );





    if (

      blockSize > 0 &&

      framesInBlock >= blockSize &&

      offset < payloadLength

    )

    {

      framesInBlock =

        0;





      if (

        !waitFlowControl(

          blockSize,

          stMinMs

        )

      )

      {

        return false;

      }

    }

  }





  return true;

}





// ============================================================

// WYŚWIETL BIEŻĄCE OKNO

// ============================================================



bool tryCurrentWindow()

{

  if (titleLength == 0 && !coolantDisplayAvailable() && !showCoolantOnRds()) {
    return false;
  }





  uint16_t window[

    DISPLAY_CHARS

  ];





  getDisplayWindow(

    window

  );





  lastAttempt =

    millis();





  bool ok =

    sendDisplayWindow(

      window

    );





  if (

    ok

  )

  {

    lastSuccessfulTx =

      millis();





    lastTxWasSuccessful =

      true;
    ++txSuccessCount;
}



  else

  {

    lastTxWasSuccessful =

      false;
    ++txFailureCount;
}





  return ok;

}





// ============================================================

// BLE WRITE CALLBACK

// ============================================================



class DisplayWriteCallbacks :

  public BLECharacteristicCallbacks

{

  void onWrite(

    BLECharacteristic *characteristic

  )

  {

    String received =

      characteristic->getValue();





    if (

      received.length() == 0

    )

    {

      return;

    }





    portENTER_CRITICAL(

      &textMux

    );





    pendingText =

      received;





    newTextAvailable =

      true;





    portEXIT_CRITICAL(

      &textMux

    );
}

};




// ============================================================

// BLE SERVER

// ============================================================



class DisplayServerCallbacks :

  public BLEServerCallbacks

{

  void onConnect(

    BLEServer *server

  )

  {

    bleConnected =

      true;
}





  void onDisconnect(

    BLEServer *server

  )

  {

    bleConnected =

      false;
delay(100);





    BLEDevice::startAdvertising();

  }

};





// ============================================================

// BLE INIT

// ============================================================



void initBLE()

{

  BLEDevice::init(

    BLE_NAME

  );





  bleServer =

    BLEDevice::createServer();





  bleServer->setCallbacks(

    new DisplayServerCallbacks()

  );





  BLEService *service =

    bleServer->createService(

      SERVICE_UUID

    );





  bleCharacteristic =

    service->createCharacteristic(

      CHAR_UUID,

      BLECharacteristic::PROPERTY_WRITE |

      BLECharacteristic::PROPERTY_WRITE_NR

    );





  bleCharacteristic->setCallbacks(

    new DisplayWriteCallbacks()

  );

  BLECharacteristic *caps = service->createCharacteristic(
      CAP_UUID, BLECharacteristic::PROPERTY_READ);
  caps->setValue("KP1|ICONS-TEST");

  service->start();





  BLEAdvertising *advertising =

    BLEDevice::getAdvertising();





  advertising->addServiceUUID(

    SERVICE_UUID

  );





  advertising->setScanResponse(

    true

  );





  BLEDevice::startAdvertising();
}





// ============================================================

// TITLE Z BLE

// ============================================================



void loadTitle(

  const String &text

)

{

  // ==========================================================

  // TEN SAM TITLE

  //

  // NIE resetujemy scrolla.

  // ==========================================================



  if (

    lastTitleText.length() > 0 &&

    text == lastTitleText

  )

  {
if (

      !lastTxWasSuccessful

    )

    {

      tryCurrentWindow();

    }





    return;

  }





  // ==========================================================

  // NOWY TITLE

  // ==========================================================



  lastTitleText =

    text;





  titleLength =

    decodeUtf8(

      text,

      titleChars,

      MAX_TITLE_CHARS

    );





  scrollPosition =

    0;





  lastScroll =

    millis();





  lastAttempt =

    0;





  lastSuccessfulTx =

    0;





  lastTxWasSuccessful =

    false;
tryCurrentWindow();

}





// ============================================================
// Komendy APK -> ESP (po BLE). Kompatybilne z tekstem historycznym.
// Uwaga: telefon, nazwa kontaktu i źródło NIE są wykrywane przez ESP.
// ============================================================
bool parseSource(String code, KrupasSource &result) {
  code.trim(); code.toUpperCase();
  if (code=="RADIO" || code=="FM") result=SRC_RADIO;
  else if (code=="BT" || code=="BLUETOOTH") result=SRC_BT;
  else if (code=="MP3" || code=="MUSIC") result=SRC_MP3;
  else if (code=="YT" || code=="YOUTUBE") result=SRC_YT;
  else if (code=="NAV" || code=="NAVIGATION") result=SRC_NAV;
  else return false;
  return true;
}
// Aktywacja protokołu tylko po jawnym rozpoznaniu @KP1|.
// Nie interpretujemy przypadkowego tytułu jako polecenia.
static int kpHex(char ch) {
  if (ch >= '0' && ch <= '9') return ch-'0';
  if (ch >= 'A' && ch <= 'F') return ch-'A'+10;
  if (ch >= 'a' && ch <= 'f') return ch-'a'+10;
  return -1;
}
static bool kpDecode(const String &encoded, String &decoded) {
  if (encoded.length() > 512) return false;
  decoded = "";
  decoded.reserve(encoded.length());
  for (size_t i=0; i<encoded.length(); ++i) {
    char ch=encoded[i];
    if (ch=='%') {
      if (i+2 >= encoded.length()) return false;
      int hi=kpHex(encoded[i+1]), lo=kpHex(encoded[i+2]);
      if (hi<0 || lo<0) return false;
      ch=(char)((hi<<4)|lo);
      i+=2;
    } else if (!((ch>='a'&&ch<='z') || (ch>='A'&&ch<='Z') ||
                  (ch>='0'&&ch<='9') || ch=='-' || ch=='_' ||
                  ch=='.' || ch=='~')) {
      return false;
    }
    if (ch=='\0' || ch=='\r' || ch=='\n' || ch=='|') return false;
    decoded += ch;
    if (decoded.length()>192) return false;
  }
  return true;
}
static void kpSwitchSource(KrupasSource source, const String &title) {
  if (callActive) { savedSource=source; savedMediaText=title; return; }
  if (activeSource != source) lastTitleText = "";
  activeSource = source;
  loadTitle(title);
}
static bool kpHandleFrame(const String &msg) {
  if (!msg.startsWith("@KP1|")) return false;
  if (msg.startsWith("@KP1|SRC|")) {
    int cut=msg.indexOf('|',9);
    if (cut<=9) { Serial.println("[KP1] bad source"); return true; }
    KrupasSource source;
    if (!parseSource(msg.substring(9,cut), source)) {
      Serial.println("[KP1] unsupported source"); return true;
    }
    String decoded;
    if (!kpDecode(msg.substring(cut+1), decoded)) {
      Serial.println("[KP1] bad encoding"); return true;
    }
    kpSwitchSource(source, decoded);
    Serial.printf("[KP1] SRC %d length %d\n",(int)source,decoded.length());
    return true;
  }
  if (msg.startsWith("@KP1|CALL|ACTIVE|")) {
    String caller;
    if (!kpDecode(msg.substring(17),caller)) {
      Serial.println("[KP1] bad caller"); return true;
    }
    if (!caller.length()) caller="TELEFON";
    if (!callActive) { savedSource=activeSource; savedMediaText=lastTitleText; }
    callActive=true;
    activeSource=SRC_BT;
    lastTitleText="";
    loadTitle(caller);
    Serial.println("[KP1] CALL ACTIVE");
    return true;
  }
  if (msg == "@KP1|CALL|END|") {
    if (callActive) {
      callActive=false;
      activeSource=savedSource;
      lastTitleText="";
      loadTitle(savedMediaText);
      Serial.println("[KP1] CALL END");
    }
    return true;
  }
  Serial.println("[KP1] unknown command ignored");
  return true;
}
void applyDisplayText(const String &message) {
  if (kpHandleFrame(message)) return;
  if (message == "CALL_END" || message == "CALL:END") {
    if (callActive) {
      callActive=false;
      activeSource=savedSource;
      lastTitleText="";
      loadTitle(savedMediaText);
      Serial.println("[CALL] end - restoring media");
    }
    return;
  }
  if (message.startsWith("CALL:")) {
    String caller=message.substring(5); caller.trim();
    if (!caller.length()) caller="TELEFON";
    if (!callActive) { savedSource=activeSource; savedMediaText=lastTitleText; }
    callActive=true;
    activeSource=SRC_BT;
    lastTitleText="";
    loadTitle(caller);
    Serial.println("[CALL] active");
    return;
  }
  if (message.startsWith("SRC:")) {
    int sep=message.indexOf('|',4);
    KrupasSource source;
    if (sep>4 && parseSource(message.substring(4,sep),source)) {
      String title=message.substring(sep+1);
      if (callActive) { savedSource=source; savedMediaText=title; return; }
      if (activeSource!=source) lastTitleText=""; // wymus aktualizacje nawet przy tym samym tytule
      activeSource=source;
      loadTitle(title);
      return;
    }
  }
  // Kompatybilnosc z komunikatami starszej APK: RADIO:tekst, BT:tekst,
  // MP3:tekst, YOUTUBE:tekst, YT:tekst oraz "Radio tekst".
  // Nie zgadujemy zrodla z samego tytulu muzyki.
  String normalized=message;
  normalized.trim();
  String upper=normalized;
  upper.toUpperCase();
  KrupasSource detected=SRC_LEGACY;
  size_t cut=0;
  const char *prefixes[]={"RADIO:","RADIO ","FM:","BT:","BLUETOOTH:","MP3:","MUSIC:","YOUTUBE:","YT:"};
  const KrupasSource types[]={SRC_RADIO,SRC_RADIO,SRC_RADIO,SRC_BT,SRC_BT,SRC_MP3,SRC_MP3,SRC_YT,SRC_YT};
  for (uint8_t i=0;i<9;i++) {
    String p=String(prefixes[i]);
    if (upper.startsWith(p)) { detected=types[i]; cut=p.length(); break; }
  }
  if (detected!=SRC_LEGACY) {
    String clean=normalized.substring(cut);
    clean.trim();
    if (callActive) { savedSource=detected; savedMediaText=clean; return; }
    if (activeSource!=detected) lastTitleText="";
    activeSource=detected;
    Serial.printf("[SOURCE] detected=%d text=%s\n",(int)detected,clean.c_str());
    loadTitle(clean);
    return;
  }
  // Przy starym, nieoznaczonym tytule nie wymyslamy ikony YouTube/MP3.
  if (callActive) { savedSource=SRC_LEGACY; savedMediaText=message; return; }
  if (activeSource!=SRC_LEGACY) lastTitleText="";
  activeSource=SRC_LEGACY;
  Serial.println("[SOURCE] legacy untagged; no icon");
  loadTitle(message);
}

// ============================================================

// SETUP

// ============================================================



void setup()

{

  Serial.begin(

    115200

  );





  delay(1000);
Serial.println(

    "========================================"

  );
Serial.println(

    "LAST WORKING BASE"

  );
Serial.println(

    "NO TITLE SCROLL RESET"

  );
Serial.println(

    "========================================"

  );





  Serial.println("[VERSION] KRUPAS OPEL DISPLAY 1.9x8 KP1 ICONS TEST (BASE x6)");
  Serial.println("[CID-TEST] v1.9x8: KP1 opt-in, glyphs unverified");
  Serial.println("[CID-TEST] OEM unchanged; ID12 position unverified");
  if (

    initCAN()

  )

  {
}



  else

  {
}





  initBLE();
}





// ============================================================

// LOOP

// ============================================================



void loop()

{

  // Pasywny odbiór MS-CAN (gdy nie trwa transmisja ISO-TP).
  pollCoolantCAN();


  // ==========================================================

  // NOWA WIADOMOŚĆ BLE

  // ==========================================================



  if (

    newTextAvailable

  )

  {

    String temp =

      "";





    portENTER_CRITICAL(

      &textMux

    );





    temp =

      pendingText;





    newTextAvailable =

      false;





    portEXIT_CRITICAL(

      &textMux

    );





    if (

      temp.length() > 0

    )

    {

      Serial.printf("[BLE RX] %u bytes\n", (unsigned int)temp.length());
      applyDisplayText(temp);

    }

  }





  // Diagnoza w Monitorze portu szeregowego (bez rozbierania auta).
  if (millis() - lastDiagnosticMs >= 30000UL) {
    lastDiagnosticMs = millis();
    Serial.printf("[HEALTH] uptime=%lus BLE=%d CAN_OK=%lu CAN_FAIL=%lu TEMP=%d title=%u\n",
      (unsigned long)(millis()/1000), (int)bleConnected,
      (unsigned long)txSuccessCount, (unsigned long)txFailureCount,
      (int)coolantDisplayAvailable(), (unsigned int)titleLength);
  }
  // Podmiana jedynie głównego pola RDS w teście temperatury.
  // Zegar, temp. zewnętrzna i dolny pasek pozostają fabryczne.
  const bool coolantOverlayActive = showCoolantOnRds();
  if (coolantOverlayActive != coolantOverlayWasActive) {
    coolantOverlayWasActive = coolantOverlayActive;
    lastTxWasSuccessful = false;
    lastAttempt = 0;
    if (coolantOverlayActive || titleLength > 0) {
      tryCurrentWindow();
    }
  }

  if (coolantOverlayActive) {
    if (!lastTxWasSuccessful) {
      if (lastAttempt == 0 || millis() - lastAttempt >= RETRY_INTERVAL_MS) {
        tryCurrentWindow();
      }
    } else if (millis() - lastSuccessfulTx >= REFRESH_INTERVAL_MS) {
      tryCurrentWindow();
    }
    delay(5);
    return; // Scroll RDS pozostaje zamrożony podczas podglądu temperatury.
  }

  // ==========================================================

  // BRAK TITLE

  // ==========================================================



  if (titleLength == 0) {
    // BLE nie jest wymagane do odswiezania temperatury z CAN.
    if (coolantDisplayAvailable()) {
      if (!lastTxWasSuccessful) {
        if (!lastAttempt || millis() - lastAttempt >= RETRY_INTERVAL_MS)
          tryCurrentWindow();
      } else if (millis() - lastSuccessfulTx >= REFRESH_INTERVAL_MS) {
        tryCurrentWindow();
      }
    }
    delay(5);
    return;
  }





  // ==========================================================

  // RETRY

  // ==========================================================



  if (

    !lastTxWasSuccessful

  )

  {

    if (

      lastAttempt == 0 ||

      millis() - lastAttempt >=

      RETRY_INTERVAL_MS

    )

    {

      tryCurrentWindow();

    }





    delay(5);



    return;

  }





  // ==========================================================

  // SCROLL

  // ==========================================================



  if (

    titleLength >

    mediaWidth()

  )

  {

    if (

      millis() - lastScroll >=

      SCROLL_INTERVAL_MS

    )

    {

      uint16_t virtualLength =

        titleLength + 3;





      uint16_t oldPosition =

        scrollPosition;





      scrollPosition++;





      if (

        scrollPosition >=

        virtualLength

      )

      {

        scrollPosition =

          0;

      }





      if (

        tryCurrentWindow()

      )

      {

        lastScroll =

          millis();

      }



      else

      {

        scrollPosition =

          oldPosition;

      }





      delay(5);



      return;

    }

  }





  // ==========================================================

  // PODTRZYMANIE KROTKIEGO TITLE

  // Długi TITLE jest podtrzymywany przez kolejne kroki scrolla.
  // Krótki TITLE odświeżamy osobno co REFRESH_INTERVAL_MS.

  // ==========================================================



  if (

    titleLength <= mediaWidth() &&

    millis() - lastSuccessfulTx >=

    REFRESH_INTERVAL_MS

  )

  {

    tryCurrentWindow();

  }





  delay(5);

}