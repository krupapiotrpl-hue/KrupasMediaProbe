/*
  1.9x22-EMPTY-REFRESH — TEST, base: user-provided 1.9x21.
  Allows transmitting and retrying an empty display window without coolant.
  No CAN/ISO-TP, source parser, GPIO, BLE UUID or temperature decoding changes.
  Arduino compilation and physical display verification still required.
*/
/*
  KRUPAS OPEL DISPLAY 1.9x21-PROTOCOL-SAFE — TEST
  Based on user-provided 1.9x20-DAB-RPLUS.
  Changes only in BLE source handling:
  - Untagged TITLE respects explicit SOURCE:DAB / SOURCE:YT / SOURCE:RADIO.
  - PHONE:OFF restores current source; BLE metadata can update behind call overlay.
  - CLEAR:ALL resets media source metadata.
  No modifications to CAN, ISO-TP, BLE UUID, coolant or display geometry.
  Requires Arduino compilation and in-car verification.
*/
/*
  KRUPAS OPEL DISPLAY 1.9x21-PROTOCOL-SAFE (TEST)
  Based on supplied 1.9x19-SCROLL-TIMING.
  FM R|, DAB+ R+|; DAB:TEXT:... and DAB:STOP protocol.
  DAB+ prefix fixed (3 chars), scroll only 9 remaining chars.
  Explicit YT: or >| remains supported. KMP must send source metadata.
  CAN, coolant, BLE UUID and ISO-TP unchanged. Requires compile and car test.
*/
/*
  1.9x21-PROTOCOL-SAFE, 2026-11-04. TEST VERSION.
  Based on the user's installed 1.9x17. Changes:
  - Scroll interval measured start-to-start, with no catch-up bursts.
  - Nonblocking, bounded serial line reader; tekst <text> test command.
  - TX duration, longest success gap, scroll count and BLE drops diagnostics.
  CAN/ISO-TP payload, timing constraints, coolant and BLE protocol retained.
  CAN sending remains synchronous: this is NOT an asynchronous transport rewrite.
  Smoothness on the physical Opel display requires vehicle verification.
  Historical comments below describe earlier releases.
*/
/*
  1.9x17-BLE-PROTOCOL-FIX (2026-11-03)
  Based on 1.9x16-PREFIX-SCROLL-250; CAN, ISO-TP and coolant paths unchanged.
  Adds ordered BLE FIFO and command parser. CALL priority; fixed source marks.
  TEST firmware: vehicle verification required, especially phone number source.
*/
/*
 KRUPAS OPEL DISPLAY 1.9x17-BLE-PROTOCOL-FIX | 2026-11-03
 Oznaczenia R|, M|, >|, T| sa rozpoznawane i oddzielane od tytulu.
 Dodatkowo obslugiwane etykiety z kreska: RADIO, RDS, MP3, MUSIC,
 YT, YOUTUBE, TELEFON, PHONE. Spacje i wielkosc liter sa pomijane.
 Pasujace podwojne prefiksy, np. "MP3: M | ...", sa usuwane.
 ESP dodaje znacznik i | w stalych pozycjach okna; przewija tylko
 tytul. Sam tekst bez oznaczenia zrodla nie jest klasyfikowany na sile.
 Bez zmian transportu CAN, BLE, temperatury i testu znakow.
 Kompilacja dla ESP32 oraz dzialanie w aucie wymagaja sprawdzenia.
 Ponizej pozostawiono historie pliku bazowego.
*/
/*
 KRUPAS OPEL DISPLAY — ESP 1.9x13 RADIO ASCII
 Data: 2026-10-26 | STATUS: TESTOWY, NIEZWERYFIKOWANY NA AUCIE
 Baza: 1.9x AUTO RECOVERY (kod dostępny w tej rozmowie).
 Baza wydania 1.9x6: lokalny plik 1/1.ino oznaczony 1.9x5.
 HISTORIA:
 1.9x13 (2026-10-26): RADIO jako bezpieczny symbol ASCII [o] (obudowa + pokretlo).
         Symbol nieruchomy; przewijany jest tylko RDS (9 znakow okna).
         Pozostale media, BLE, CAN, ISO-TP, recovery i PLYN bez zmian.
         Baza: potwierdzona w aucie 1.9x10; 1.9x12 odrzucona (Unicode).
 1.9x10 (2026-10-25): osobny, staly znacznik zrodla mediow (R/M/>),
         przewijanie tylko tytulu; obsluga opcjonalnych prefiksow BLE
         RADIO:/RDS:/MP3:/YT:; odswiezanie do 800 ms; podtrzymanie
         ostatniego pomiaru PLYN, znak ? gdy CAN pomiar jest nieaktualny;
         mechanizm recovery MCP2515 po serii bledow transmisji.
         1.9x7 pozostaje nienaruszona. Wymaga testu w samochodzie.
 1.9x7 (2026-10-23): przywrocono selektor fontu tytulu z wersji 1.9x
         (fS_g -> fS_d, 0x67 -> 0x64). Bez zmiany wysokosci czcionki.
         Nie dodano ikonek; logika BLE/CAN/temperatury bez zmian.
         Kompilacja niepotwierdzona; wymagany test wyswietlacza.
 1.9x6 (2026-10-23): wspolny identyfikator FIRMWARE_VERSION w logu
         startowym i okresowym HEALTH. Zachowana logika 1.9x5.
         Bez nowych mechanizmow recovery i bez deklaracji naprawy CID.
         Starsze wpisy historii ponizej pochodza z pliku bazowego.
 1.9x5: nowy unikalny identyfikator wersji na prośbę użytkownika.
         Bez zmian funkcjonalnych względem 1.9x4.
 1.9x4: poprawiona kolejność deklaracji coolantDisplayAvailable()
         po coolantValid, coolantLastFrameMs i COOLANT_STALE_MS.
 1.9x3: unikalny numer wersji, nagłówek historii i identyfikator Serial.
         Brak zmian funkcjonalnych względem dostępnej bazy 1.9x.
 1.9x2: uzgodniona nazwa pośrednia; niezweryfikowana jako wydany plik.
 1.9x:  odświeżanie RDS / temperatury i diagnostyka, wersja testowa.
 UWAGA: zanik linii CID nie jest jeszcze potwierdzony jako naprawiony.
 Nie wgrywać jednocześnie zasilania 12–24 V i USB bez weryfikacji płytki.
*/
// KRUPAS OPEL DISPLAY v1.9x13 - RADIO ASCII; verified base 1.9x10.
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

  BAZA DO POROWNANIA:

    v1.9x7

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

#define FIRMWARE_VERSION "1.9x22-EMPTY-REFRESH"

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

BLEServer *bleServer = nullptr;

BLECharacteristic *bleCharacteristic = nullptr;

bool bleConnected = false;

// ============================================================

// BLE RX

// ============================================================

static const size_t PENDING_TEXT_MAX_BYTES = 384;
// FIFO: pojedynczy bufor z 1.9x16 gubil komendy przy seryjnych BLE WRITE.
static const uint8_t BLE_QUEUE_DEPTH = 16;
char bleQueue[BLE_QUEUE_DEPTH][PENDING_TEXT_MAX_BYTES + 1] = {{0}};
uint8_t bleQueueHead = 0, bleQueueTail = 0, bleQueueCount = 0;
volatile uint32_t bleQueueDrops = 0;

portMUX_TYPE textMux =

  portMUX_INITIALIZER_UNLOCKED;

// ============================================================

// TITLE

// ============================================================

const uint16_t MAX_TITLE_CHARS = 96;
uint16_t titleChars[MAX_TITLE_CHARS] = {0};
uint16_t titleLength = 0;
String lastTitleText = "";    // tekst + zrodlo, do deduplikacji

// Typ zrodla jest opcjonalny: bez prefiksu BLE pozostaw pelne okno.
enum MediaSource : uint8_t {
  MEDIA_UNKNOWN = 0,
  MEDIA_RADIO,
  MEDIA_DAB,
  MEDIA_MP3,
  MEDIA_YOUTUBE,
  MEDIA_PHONE
};
MediaSource mediaSource = MEDIA_UNKNOWN;

// Kazde pole kodujemy jako 12 znakow UTF-16BE (jak w 1.9x7).
const uint8_t DISPLAY_CHARS = 12;
const uint32_t SCROLL_INTERVAL_MS = 110UL;
const uint32_t REFRESH_INTERVAL_MS = 800UL;
const uint32_t RETRY_INTERVAL_MS = 450UL;
const uint32_t FLOW_CONTROL_TIMEOUT_MS = 150UL;
const uint32_t CAN_RECOVERY_INTERVAL_MS = 10000UL;
const uint8_t CAN_RECOVERY_FAILURES = 6;

uint16_t scrollPosition = 0;
uint32_t lastSuccessfulTx = 0;
uint32_t lastAttempt = 0;
uint32_t lastScroll = 0;
uint32_t lastCanRecovery = 0;
bool lastTxWasSuccessful = false;
uint32_t txSuccessCount = 0;
uint32_t txFailureCount = 0;
uint8_t consecutiveTxFailures = 0;
uint32_t lastDiagnosticMs = 0;
bool coolantDirty = false;
bool displayPending = false;
uint32_t txLastDurationMs = 0, txMaxDurationMs = 0;
uint32_t txMaxSuccessGapMs = 0, scrollStepCount = 0;

// Bounded reader: never wait for a newline inside the main loop.
String serialLine;
bool serialDiscard = false;
bool readSerialCommand(String &command) {
  for (uint16_t consumed = 0; consumed < 96 && Serial.available(); ++consumed) {
    const char ch = (char)Serial.read();
    if (ch == '\r' || ch == '\n') {
      if (serialDiscard) {
        serialDiscard = false;
        serialLine = "";
        Serial.println("[SERIAL] Line too long; discarded");
        return false;
      }
      if (!serialLine.length()) continue;
      command = serialLine;
      serialLine = "";
      return true;
    }
    if (serialDiscard) continue;
    if (serialLine.length() >= PENDING_TEXT_MAX_BYTES) {
      serialDiscard = true;
      serialLine = "";
    } else serialLine += ch;
  }
  return false;
}

// ============================================================
// PLYN z MS-CAN 0x4EC. NIE zamieniamy pomiaru w ramke sterujaca.
// Temperatura stale pozostaje w polu ID12, nie w dolnym pasku OEM.
// Ostatni pomiar jest widoczny; ? oznacza pomiar nieaktualny.
// Nie wolno interpretowac starej wartosci jako nowego odczytu!
// ============================================================
const uint16_t COOLANT_FRAME_ID = 0x4EC;
const uint32_t COOLANT_STALE_MS = 20000UL;

bool coolantValid = false;
int16_t coolantC = 0;
uint32_t coolantLastFrameMs = 0;
bool coolantWasStale = false;

bool coolantDisplayAvailable() {
  return coolantValid;  // nie kasuj wartosci przez przerwe w transmisji CAN
}

bool coolantIsStale() {
  return coolantValid && (millis() - coolantLastFrameMs > COOLANT_STALE_MS);
}

void processCoolantFrame(uint16_t id, const uint8_t *data, uint8_t len) {
  if (id != COOLANT_FRAME_ID || len != 5 || data[0] != 0x46) return;
  const int16_t measured = (int16_t)data[2] - 40;
  if (measured < -40 || measured > 140) return;

  const bool changed = !coolantValid || coolantC != measured;
  const bool recoveredFromStale = coolantIsStale();
  coolantC = measured;
  coolantValid = true;
  coolantLastFrameMs = millis();
  if (changed || recoveredFromStale) coolantDirty = true;
  if (changed) {
    Serial.printf("[TEMP] PLYN %d C (CAN 0x4EC)\n", (int)coolantC);
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

// Test znakow GID. Nie wysylamy emoji U+1F4FB (poza BMP).
// Wybor reczny przez Serial; domyslnie bezpieczne R|.
const uint16_t RADIO_GLYPHS[] = {
  0x0052, // R
  0x00AE, // znak zastrzezony
  0x25A3, // kwadrat z mniejszym kwadratem
  0x25A0, // czarny kwadrat
  0x25C9, // okrag z kropka
  0x25CE, // okrag w okregu
  0x266B, // nuty
  0x25A4, // kwadrat kreskowany
  0x25B6  // trojkat
};
const uint8_t RADIO_GLYPH_COUNT = sizeof(RADIO_GLYPHS) / sizeof(RADIO_GLYPHS[0]);
uint8_t radioGlyphIndex = 0;

uint8_t mediaPrefixWidth() {
  return mediaSource == MEDIA_UNKNOWN ? 0 : (mediaSource == MEDIA_DAB ? 3 : 2);
}
uint8_t mediaTextWidth() {
  return DISPLAY_CHARS - mediaPrefixWidth();
}

void getDisplayWindow(uint16_t *window) {
  for (uint8_t i = 0; i < DISPLAY_CHARS; ++i) window[i] = ' ';
  uint8_t from = mediaPrefixWidth();
  if (from) {
    switch (mediaSource) {
      case MEDIA_RADIO:   window[0] = RADIO_GLYPHS[radioGlyphIndex]; window[1] = '|'; break;
      case MEDIA_DAB:     window[0] = 'R'; window[1] = '+'; window[2] = '|'; break;
      case MEDIA_MP3:     window[0] = 'M'; window[1] = '|'; break;
      case MEDIA_YOUTUBE: window[0] = '>'; window[1] = '|'; break;
      case MEDIA_PHONE:   window[0] = 'T'; window[1] = '|'; break;
      default: break;
    }
  }
  if (!titleLength) return;
  const uint8_t width = mediaTextWidth();
  const bool scrolling = titleLength > width;
  const uint16_t virtualLength =
    scrolling ? titleLength + width + 3 : titleLength;
  // Wylacznie okno tytulu podlega przesuwaniu. Pozycje 0 i 1
  // nie sa nigdy nadpisywane przez petle przewijania.
  for (uint8_t i = 0; i < width; ++i) {
    const uint16_t idx = scrolling ? scrollPosition + i : i;
    if (idx < titleLength) window[from + i] = titleChars[idx];
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

    0x00, 0x64,

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
    if (coolantValid) {
      const uint16_t label[] = {'P', 0x0141, 'Y', 'N', ' '};
      for (uint8_t i = 0; i < 5; ++i) third[i] = label[i];
      char number[7];
      snprintf(number, sizeof(number), "%d", (int)coolantC);
      uint8_t pos = 5;
      for (uint8_t i = 0; number[i] && pos < 10; ++i)
        third[pos++] = (uint8_t)number[i];
      if (pos < 12) third[pos++] = 0x00B0;
      if (pos < 12) third[pos++] = 'C';
      if (coolantIsStale() && pos < 12) third[pos++] = '?';
    }
    for (uint8_t i = 0; i < 12; ++i) {
      payload[54 + 2 * i] = (uint8_t)(third[i] >> 8);
      payload[55 + 2 * i] = (uint8_t)(third[i] & 0xFF);
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

  txLastDurationMs = millis() - lastAttempt;
  if (txLastDurationMs > txMaxDurationMs) txMaxDurationMs = txLastDurationMs;
  if (ok && txSuccessCount) {
    const uint32_t gap = millis() - lastSuccessfulTx;
    if (gap > txMaxSuccessGapMs) txMaxSuccessGapMs = gap;
  }
  if (

    ok

  )

  {

    lastSuccessfulTx =

      millis();

    lastTxWasSuccessful =

      true;
    ++txSuccessCount;
      consecutiveTxFailures = 0;
      coolantDirty = false;
      displayPending = false;
}

  else

  {

    lastTxWasSuccessful =

      false;
    ++txFailureCount;
      if (consecutiveTxFailures < 255) ++consecutiveTxFailures;
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

    const size_t count = received.length() < PENDING_TEXT_MAX_BYTES
        ? received.length() : PENDING_TEXT_MAX_BYTES;
      // Nie nadpisuj poprzedniej komendy; na pelnej kolejce zachowaj
      // dotychczasowe polecenia, zlicz odrzucone wiadomosci.
      if (bleQueueCount < BLE_QUEUE_DEPTH) {
        memcpy(bleQueue[bleQueueTail], received.c_str(), count);
        bleQueue[bleQueueTail][count] = 0;
        bleQueueTail = (bleQueueTail + 1) % BLE_QUEUE_DEPTH;
        ++bleQueueCount;
      } else {
        ++bleQueueDrops;
      }

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

// Rozpoznajemy zrodlo z jawnymi tagami APK, np. "RADIO: VOX FM".
// Bez tagu tytul nadal wyswietlany jest w calosci; ESP nie zgaduje
// po losowym tytule, czy pochodzi z YouTube, MP3 czy innej aplikacji.
bool extractPrefix(String &text, const String &upper, const char *prefix) {
  const size_t n = strlen(prefix);
  if (!upper.startsWith(prefix)) return false;
  text = text.substring(n);
  text.trim();
  return true;
}

// Jawny znacznik z APK: R|, M|, >|, T| oraz pelne nazwy zrodel.
// Prefiks usuwamy z tytulu tylko po dopasowaniu calej etykiety.
bool extractMediaBarPrefix(String &text, MediaSource &source) {
  const int separator = text.indexOf('|');
  if (separator < 0) return false;
  String marker = text.substring(0, separator);
  marker.trim();
  marker.toUpperCase();
  if (marker == "R+" || marker == "DAB" || marker == "DAB+") {
    source = MEDIA_DAB;
  } else if (marker == "R" || marker == "RADIO" || marker == "RDS") {
    source = MEDIA_RADIO;
  } else if (marker == "M" || marker == "MP3" || marker == "MUSIC") {
    source = MEDIA_MP3;
  } else if (marker == ">" || marker == "YT" || marker == "YOUTUBE") {
    source = MEDIA_YOUTUBE;
  } else if (marker == "T" || marker == "TELEFON" || marker == "PHONE") {
    source = MEDIA_PHONE;
  } else {
    return false;
  }
  text = text.substring(separator + 1);
  text.trim();
  return true;
}


// 1.9x23 RDS DASH GUARD.
// Remove empty OEM metadata separators but preserve separators inside actual
// station names, RDS announcements and music titles.
// Applied ONLY to FM/RDS, never to MP3/YouTube/DAB/telephone metadata.
String cleanRdsSeparators(String input) {
  input.replace("\\r", " ");
  input.replace("\\n", " ");
  input.replace("\\t", " ");
  input.replace("\xE2\x80\x93", "-"); // UTF-8 en dash
  input.replace("\xE2\x80\x94", "-"); // UTF-8 em dash
  input.trim();

  // Trim leading/trailing runs of separators: "- -", "--", "| -", etc.
  // Do not touch inner "ARTIST - TITLE" or "RADIO - NEWS" separators.
  bool changed = true;
  while (changed && input.length()) {
    changed = false;
    input.trim();
    while (input.length() && (input[0] == '-' || input[0] == '|')) {
      input.remove(0, 1);
      input.trim();
      changed = true;
    }
    while (input.length() &&
           (input[input.length()-1] == '-' || input[input.length()-1] == '|')) {
      input.remove(input.length()-1);
      input.trim();
      changed = true;
    }
  }

  // A lone dash-separated group is never RDS data.
  String check = input;
  check.replace(" ", "");
  check.replace("-", "");
  check.replace("|", "");
  if (check.length() == 0) return "";

  // Normalize whitespace left after stripping decorative separators.
  while (input.indexOf("  ") >= 0) input.replace("  ", " ");
  return input;
}

void parseMediaText(const String &raw, MediaSource &source, String &title) {
  title = raw;
  title.replace("\r", " ");
  title.replace("\n", " ");
  title.replace("\t", " ");
  title.trim();
  String upper = title;
  upper.toUpperCase();
  source = MEDIA_UNKNOWN;

  if (extractMediaBarPrefix(title, source)) {
    // Rozpoznaj etykiete z | przed starszymi prefiksami ze spacja.
  } else if (extractPrefix(title, upper, "DAB:TEXT:") ||
             extractPrefix(title, upper, "DAB+:") ||
             extractPrefix(title, upper, "DAB:")) {
    source = MEDIA_DAB;
  } else if (extractPrefix(title, upper, "RADIO:") ||
      extractPrefix(title, upper, "RADIO ") ||
      extractPrefix(title, upper, "RDS:")) {
    source = MEDIA_RADIO;
  } else if (extractPrefix(title, upper, "MP3:") ||
             extractPrefix(title, upper, "MP3 ") ||
             extractPrefix(title, upper, "MUSIC:")) {
    source = MEDIA_MP3;
  } else if (extractPrefix(title, upper, "YOUTUBE:") ||
             extractPrefix(title, upper, "YOUTUBE ") ||
             extractPrefix(title, upper, "YT:")) {
    source = MEDIA_YOUTUBE;
  } else if (extractPrefix(title, upper, "TELEFON:") ||
             extractPrefix(title, upper, "PHONE:")) {
    source = MEDIA_PHONE;
  } else if (((upper.startsWith("FM ") || upper.startsWith("AM ")) &&
              upper.length() <= 16) ||
              (upper.endsWith(" FM") && upper.length() <= 12)) {
    source = MEDIA_RADIO;
    // FM 95.6 zostawiamy, bo to wlasciwa czestotliwosc.
  }

  // Znacznik i | pozostaja w dwoch pierwszych pozycjach okna.
  // Scroll obejmuje tylko tytul. Jawny tag zrodla ma pierwszenstwo,
  // np. "MP3: R | utwor" nie zostanie zmienione na radio.
  String markerTitle = title;
  MediaSource markerSource = MEDIA_UNKNOWN;
  if (extractMediaBarPrefix(markerTitle, markerSource) &&
      (source == MEDIA_UNKNOWN || source == markerSource)) {
    title = markerTitle;
    source = markerSource;
  }

  // Ochrona przed zduplikowanym prefiksem wysylanym przez APK.
  // Po rozpoznaniu zrodla usuwamy tylko zgodne, wiodace etykiety.
  // Dziala dla R|R|VOX, R | R | VOX, RADIO: R|VOX i M|M|UTWOR.
  // Znaczniki innych zrodel nie sa usuwane (brak zgadywania).
  if (source != MEDIA_UNKNOWN) {
    for (uint8_t pass = 0; pass < 8; ++pass) {
      String candidate = title;
      MediaSource candidateSource = MEDIA_UNKNOWN;
      if (extractMediaBarPrefix(candidate, candidateSource) &&
          candidateSource == source) {
        title = candidate;
        continue;
      }
      // APK moze wyslac powtorzony tekstowy znacznik RADIO:/MP3:.
      String candidateUpper = candidate;
      candidateUpper.toUpperCase();
      bool removed = false;
      switch (source) {
        case MEDIA_DAB:
          removed = extractPrefix(candidate, candidateUpper, "DAB:TEXT:") ||
                    extractPrefix(candidate, candidateUpper, "DAB+:") ||
                    extractPrefix(candidate, candidateUpper, "DAB:");
          break;
        case MEDIA_RADIO:
          removed = extractPrefix(candidate, candidateUpper, "RADIO:") ||
                    extractPrefix(candidate, candidateUpper, "RDS:");
          break;
        case MEDIA_MP3:
          removed = extractPrefix(candidate, candidateUpper, "MP3:");
          break;
        case MEDIA_YOUTUBE:
          removed = extractPrefix(candidate, candidateUpper, "YOUTUBE:") ||
                    extractPrefix(candidate, candidateUpper, "YT:");
          break;
        case MEDIA_PHONE:
          removed = extractPrefix(candidate, candidateUpper, "TELEFON:") ||
                    extractPrefix(candidate, candidateUpper, "PHONE:");
          break;
        default: break;
      }
      if (!removed) break;
      title = candidate;
    }
  }

  // Guard FM/RDS only; MP3, DAB, YT and phone title formatting unchanged.
  if (source == MEDIA_RADIO) title = cleanRdsSeparators(title);
}

void loadTitle(const String &raw) {
  MediaSource nextSource;
  String title;
  parseMediaText(raw, nextSource, title);

  // BLE moze jawnie wyczyscic tytul; nie zerujemy danych przy zerwaniu BLE.
  String upper = title;
  upper.toUpperCase();
  if (upper == "<CLEAR>" || upper == "<STOP>") {
    title = "";
    nextSource = MEDIA_UNKNOWN;
  }
  // Uwaga: stale rowne zdarzenia nie resetuja scrolla ani wyswietlacza.
  const String key = String((int)nextSource) + ":" + title;
  if (key == lastTitleText) {
    return;
  }
  displayPending = true;
  lastTitleText = key;
  mediaSource = nextSource;
  titleLength = decodeUtf8(title, titleChars, MAX_TITLE_CHARS);
  scrollPosition = 0;
  lastScroll = millis();
  lastAttempt = 0;
  lastTxWasSuccessful = false;
  Serial.print("[MEDIA] source=");
  Serial.print((int)mediaSource);
  Serial.print(" title=");
  Serial.println(title);
  tryCurrentWindow();
}

// ============================================================
// BLE protocol 1.9x17. Command/control != display text.
// Compatibility: FM:, RDS:, TITLE:, SOURCE:, CALL:, PHONE:,
// MEDIA:STOP, CLEAR:RDS, CLEAR:FM, CLEAR:MEDIA, CLEAR:ALL.
// Unknown tagged commands are ignored, not rendered literally.
// ============================================================
String radioFrequency = "";
String radioRds = "";
String mediaTitle = "";
MediaSource mediaTitleSource = MEDIA_UNKNOWN;
MediaSource selectedSource = MEDIA_UNKNOWN;
String dabText = "";
String callText = "";
bool callActive = false;
// Zrodlo pozostaje aktywne podczas rozmowy; PHONE:OFF odslania je ponownie.
// Metadane naplywajace podczas rozmowy aktualizuja zapisane zrodlo.


void redrawActiveSource() {
  if (callActive) {
    loadTitle("PHONE:" + (callText.length() ? callText : String("Rozmowa")));
    return;
  }
  if (selectedSource == MEDIA_DAB) {
    if (dabText.length()) loadTitle("DAB:" + dabText);
    else loadTitle("<CLEAR>");
    return;
  }
 if (selectedSource == MEDIA_RADIO ||
    (selectedSource == MEDIA_UNKNOWN &&
     (radioRds.length() || radioFrequency.length()))) {

  String rds = cleanRdsSeparators(radioRds);
  String freq = radioFrequency;

  freq.trim();

  String rdsCheck = rds;
  String freqCheck = freq;

  rdsCheck.toUpperCase();
  freqCheck.toUpperCase();

  // Odrzucamy puste informacje zamiast nazwy stacji
  if (rdsCheck == "MHZ" || rdsCheck == "FM MHZ" ||
      rdsCheck == "FM") {
    rds = "";
  }

  if (freqCheck == "MHZ" || freqCheck == "FM MHZ" ||
      freqCheck == "FM") {
    freq = "";
  }

  if (rds.length()) {
    loadTitle("RADIO:" + rds);
  }
  else if (freq.length()) {
    loadTitle("RADIO:" + freq);
  }
  else {
    loadTitle("RADIO:Radio FM");
  }

  return;
}
  if (mediaTitle.length()) {
    const char *tag = selectedSource == MEDIA_YOUTUBE ? "YT:" :
                      selectedSource == MEDIA_PHONE ? "PHONE:" : "MP3:";
    loadTitle(String(tag) + mediaTitle);
    return;
  }
  // Do not display protocol strings when the active source has no metadata.
  loadTitle("<CLEAR>");
}

void handleBleMessage(String message) {
  message.trim();
  if (!message.length()) return;
  String upper = message;
  upper.toUpperCase();
  // Toleruj dodatkowa spacje po dwukropku w komendach sterujacych.
  String control = upper;
  control.replace(": ", ":");
  if (control == "MEDIA:STOP" || control == "CLEAR:RDS" ||
      control == "CLEAR:FM" || control == "CLEAR:MEDIA" ||
      control == "CLEAR:TITLE" || control == "CLEAR:ALL" ||
      control == "CALL:OFF" || control == "PHONE:OFF") upper = control;

  if (upper.startsWith("SOURCE:")) {
    String value = upper.substring(7); value.trim();
    if (value == "DAB" || value == "DAB+" || value == "DABZ")
      selectedSource = MEDIA_DAB;
    else if (value == "RADIO" || value == "FM" || value == "RDS")
      selectedSource = MEDIA_RADIO;
    else if (value == "MP3" || value == "MUSIC" || value == "USB")
      selectedSource = MEDIA_MP3;
    else if (value == "YT" || value == "YOUTUBE")
      selectedSource = MEDIA_YOUTUBE;
    else if (value == "PHONE" || value == "CALL" || value == "TELEFON") {
      callActive = true;
      redrawActiveSource();
      return;
    } else if (value == "BT" || value == "BLUETOOTH") {
      // BT alone is ambiguous: music streaming does not imply a call.
      selectedSource = MEDIA_MP3;
    } else if (value == "OFF" || value == "NONE" || value == "UNKNOWN")
      selectedSource = MEDIA_UNKNOWN;
    else return;
    redrawActiveSource();
    return;
  }

  if (upper == "CALL:OFF" || upper == "PHONE:OFF" || upper == "CALL:END" ||
      upper == "PHONE:END" || upper == "CALL:STOP") {
    callActive = false;
    callText = "";
    redrawActiveSource();
    return;
  }
  if (upper.startsWith("CALL:") || upper.startsWith("PHONE:")) {
    callText = message.substring(message.indexOf(':') + 1); callText.trim();
    callActive = true;
    redrawActiveSource();
    return;
  }
  if (upper == "DAB:STOP" || upper == "CLEAR:DAB") {
    dabText = "";
    if (selectedSource == MEDIA_DAB) selectedSource = MEDIA_UNKNOWN;
    redrawActiveSource();
    return;
  }
  if (upper.startsWith("DAB:TEXT:") || upper.startsWith("DAB+:")) {
    const int start = upper.startsWith("DAB:TEXT:") ? 9 : 5;
    dabText = message.substring(start); dabText.trim();
    selectedSource = MEDIA_DAB;
    redrawActiveSource();
    return;
  }
  if (upper == "MEDIA:STOP" || upper == "CLEAR:MEDIA" || upper == "CLEAR:TITLE") {
    mediaTitle = "";
    mediaTitleSource = MEDIA_UNKNOWN;
    if (selectedSource != MEDIA_RADIO && selectedSource != MEDIA_DAB) {
      selectedSource = (radioRds.length() || radioFrequency.length()) ? MEDIA_RADIO : MEDIA_UNKNOWN;
    }
    redrawActiveSource();
    return;
  }
  if (upper == "CLEAR:RDS") {
    radioRds = "";
    redrawActiveSource();
    return;
  }
  if (upper == "CLEAR:FM") {
    radioFrequency = "";
    redrawActiveSource();
    return;
  }
  if (upper == "CLEAR:ALL" || upper == "<CLEAR>" || upper == "<STOP>") {
    radioRds = ""; radioFrequency = ""; mediaTitle = ""; dabText = "";
    mediaTitleSource = MEDIA_UNKNOWN;
    selectedSource = MEDIA_UNKNOWN;
    if (!callActive) redrawActiveSource();
    return;
  }
  if (upper.startsWith("FM:")) {
    radioFrequency = message.substring(3); radioFrequency.trim();
    if (!callActive && (selectedSource == MEDIA_UNKNOWN || selectedSource == MEDIA_RADIO)) {
      selectedSource = MEDIA_RADIO;
      redrawActiveSource();
    }
    return;
  }
  if (upper.startsWith("RDS:")) {
    radioRds = cleanRdsSeparators(message.substring(4));
    if (!callActive && (selectedSource == MEDIA_UNKNOWN || selectedSource == MEDIA_RADIO)) {
      selectedSource = MEDIA_RADIO;
      redrawActiveSource();
    }
    return;
  }
  if (upper.startsWith("TITLE:")) {
    String content = message.substring(6); content.trim();
    MediaSource inferred; String normalized;
    parseMediaText(content, inferred, normalized);
    // TITLE bez jawnego znacznika dziedziczy SOURCE: ustawione przez APK.
    // Nie wolno zgadywac MP3, gdy aktywny jest DAB+ lub YouTube.
    if (inferred == MEDIA_UNKNOWN && selectedSource == MEDIA_DAB)
      inferred = MEDIA_DAB;
    else if (inferred == MEDIA_UNKNOWN && selectedSource == MEDIA_YOUTUBE)
      inferred = MEDIA_YOUTUBE;
    else if (inferred == MEDIA_UNKNOWN && selectedSource == MEDIA_RADIO)
      inferred = MEDIA_RADIO;
    mediaTitle = normalized;
    mediaTitleSource = inferred;
    if (inferred == MEDIA_DAB) {
      dabText = normalized; selectedSource = MEDIA_DAB;
    } else if (inferred == MEDIA_RADIO) {
      radioRds = cleanRdsSeparators(normalized);
      selectedSource = MEDIA_RADIO;
    } else if (inferred == MEDIA_PHONE) {
      callText = normalized; callActive = true;
    } else {
      if (inferred == MEDIA_YOUTUBE) selectedSource = MEDIA_YOUTUBE;
      else if (inferred == MEDIA_MP3) selectedSource = MEDIA_MP3;
      else if (selectedSource == MEDIA_UNKNOWN)
        selectedSource = MEDIA_MP3;
    }
    redrawActiveSource();
    return;
  }
  if (upper.startsWith("DAB:") || upper.startsWith("DAB+:") || upper.startsWith("R+|") ||
      upper.startsWith("RADIO:") || upper.startsWith("MP3:") ||
      upper.startsWith("MUSIC:") || upper.startsWith("YT:") ||
      upper.startsWith("YOUTUBE:") || upper.startsWith("TELEFON:") ||
      upper.startsWith("R|") || upper.startsWith("M|") ||
      upper.startsWith(">|") || upper.startsWith("T|")) {
    MediaSource inferred; String normalized;
    parseMediaText(message, inferred, normalized);
    if (inferred == MEDIA_DAB) {
      dabText = normalized; selectedSource = MEDIA_DAB;
    } else if (inferred == MEDIA_RADIO) {
      radioRds = cleanRdsSeparators(normalized); selectedSource = MEDIA_RADIO;
    } else if (inferred == MEDIA_PHONE) {
      callText = normalized; callActive = true;
    } else {
      mediaTitle = normalized; mediaTitleSource = inferred;
      selectedSource = inferred;
    }
    redrawActiveSource();
    return;
  }
  // Legacy untagged plain text: only text without colon is accepted.
  if (message.indexOf(':') < 0) {
    mediaTitle = message;
    if (selectedSource == MEDIA_UNKNOWN) selectedSource = MEDIA_MP3;
    redrawActiveSource();
  } else {
    Serial.println("[BLE] Unknown control ignored: " + message);
  }
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

  Serial.println("[CID-TEST] ESP " FIRMWARE_VERSION ": media + coolant, refresh 800ms");
  Serial.println("[CID-TEST] OEM unchanged; ID12 position unverified");
  if (initCAN()) Serial.println("[CAN] READY");
  else Serial.println("[CAN] INIT FAILED");

  initBLE();
  Serial.println("[ZNAKI] Polecenia: TEST, NEXT, PREV, R, tekst <dlugi napis>. Testowac na postoju.");
}

// ============================================================

// LOOP

// ============================================================

void loop() {
  // Polecenia monitora szeregowego (115200, koniec linii: Newline):
  // TEST = demonstracja radia, NEXT / PREV = wybor znaku, R = przywroc R.
  String rawCommand;
  if (readSerialCommand(rawCommand)) {
    rawCommand.trim();
    String cmd = rawCommand;
    cmd.toUpperCase();
    if (cmd.startsWith("TEKST ")) {
      // Disconnect APK BLE for an isolated test; media events can replace it.
      String testText = rawCommand.substring(6);
      testText.trim();
      if (testText.length()) loadTitle("RADIO:" + testText);
    } else if (cmd == "TEST") {
      radioGlyphIndex = 0;
      loadTitle("RADIO: TEST ZNAKU");
    } else if (cmd == "NEXT" || cmd == "N") {
      radioGlyphIndex = (radioGlyphIndex + 1) % RADIO_GLYPH_COUNT;
    } else if (cmd == "PREV" || cmd == "P") {
      radioGlyphIndex = (radioGlyphIndex + RADIO_GLYPH_COUNT - 1) % RADIO_GLYPH_COUNT;
    } else if (cmd == "R" || cmd == "STOP") {
      radioGlyphIndex = 0;
    } else if (cmd.length()) {
      Serial.println("[ZNAKI] TEST, NEXT, PREV, R, tekst <dlugi napis>");
    }
    if (cmd == "TEST" || cmd == "NEXT" || cmd == "N" ||
        cmd == "PREV" || cmd == "P" || cmd == "R" || cmd == "STOP") {
      Serial.printf("[ZNAKI] nr=%u/%u kod=U+%04X\n",
                    (unsigned)radioGlyphIndex,
                    (unsigned)(RADIO_GLYPH_COUNT - 1),
                    (unsigned)RADIO_GLYPHS[radioGlyphIndex]);
      if (mediaSource == MEDIA_RADIO) tryCurrentWindow();
      else Serial.println("[ZNAKI] Aby zobaczyc znak, wyslij TEST.");
    }
  }
  // Zbieranie ramek CAN bez aktywnych zapytan OBD.
  pollCoolantCAN();

  // Drain the ordered BLE FIFO. Keep CAN polling responsive.
  for (uint8_t processed = 0; processed < BLE_QUEUE_DEPTH; ++processed) {
    char copy[PENDING_TEXT_MAX_BYTES + 1] = {0};
    bool hasMessage = false;
    portENTER_CRITICAL(&textMux);
    if (bleQueueCount) {
      memcpy(copy, bleQueue[bleQueueHead], sizeof(copy));
      bleQueueHead = (bleQueueHead + 1) % BLE_QUEUE_DEPTH;
      --bleQueueCount;
      hasMessage = true;
    }
    portEXIT_CRITICAL(&textMux);
    if (!hasMessage) break;
    if (copy[0]) handleBleMessage(String(copy));
  }

  const uint32_t now = millis();
  if (now - lastDiagnosticMs >= 30000UL) {
    lastDiagnosticMs = now;
    Serial.printf("[HEALTH] ESP " FIRMWARE_VERSION
      " uptime=%lus BLE=%d CAN_OK=%lu CAN_FAIL=%lu TEMP=%d STALE=%d title=%u src=%d BLE_DROP=%lu TX_MS=%lu TX_MAX=%lu GAP_MAX=%lu SCROLL=%lu\n",
      (unsigned long)(now / 1000), (int)bleConnected,
      (unsigned long)txSuccessCount, (unsigned long)txFailureCount,
      (int)coolantDisplayAvailable(), (int)coolantIsStale(),
      (unsigned int)titleLength, (int)mediaSource, (unsigned long)bleQueueDrops,
      (unsigned long)txLastDurationMs, (unsigned long)txMaxDurationMs,
      (unsigned long)txMaxSuccessGapMs, (unsigned long)scrollStepCount);
    // Maxima cover each diagnostic interval, not the whole uptime.
    txMaxDurationMs = 0;
    txMaxSuccessGapMs = 0;
  }

  // Nie resetujemy CAN przy pojedynczym zakloceniu OEM/CID.
  if (consecutiveTxFailures >= CAN_RECOVERY_FAILURES &&
      now - lastCanRecovery >= CAN_RECOVERY_INTERVAL_MS) {
    lastCanRecovery = now;
    Serial.println("[RECOVERY] MCP2515 reinit after TX failures");
    if (initCAN()) {
      Serial.println("[RECOVERY] CAN READY");
      consecutiveTxFailures = 0;
    } else {
      Serial.println("[RECOVERY] CAN NOT READY");
    }
    lastTxWasSuccessful = false;
    lastAttempt = 0;
  }

  // Bez media: tez utrzymujemy pole temperatury na ekranie.
  if (titleLength == 0 && mediaSource == MEDIA_UNKNOWN &&
      !coolantValid && !displayPending) {
    delay(5);
    return;
  }

  const bool stale = coolantIsStale();
  const bool staleTransition = stale != coolantWasStale;
  if (staleTransition) {
    coolantWasStale = stale;
    coolantDirty = true;
    if (stale) Serial.println("[TEMP] STALE - last measured value marked ?");
  }

  if (!lastTxWasSuccessful) {
    if (lastAttempt == 0 || now - lastAttempt >= RETRY_INTERVAL_MS)
      tryCurrentWindow();
    delay(5);
    return;
  }

  if (titleLength > mediaTextWidth() &&
      now - lastScroll >= SCROLL_INTERVAL_MS) {
    const uint16_t ringLength =
    titleLength + mediaTextWidth() + 3;
    const uint16_t oldPosition = scrollPosition;
    scrollPosition = (scrollPosition + 1) % ringLength;
    // Start-to-start timing: transmission time is included in the 250 ms.
    // Re-anchor on each attempt; never send a burst to catch up after a stall.
    lastScroll = now;
    if (tryCurrentWindow()) ++scrollStepCount;
    else scrollPosition = oldPosition;
    delay(5);
    return;
  }

  // Wysylka co 800 ms rowniez dla krotkiego RDS i bez mediow.
  // Dodatkowo wysylamy zmieniona temperature bez czekania 800 ms,
  // lecz nie czesciej niz co 250 ms (bez floodowania magistrali).
  if ((coolantDirty && now - lastSuccessfulTx >= 250UL) ||
      now - lastSuccessfulTx >= REFRESH_INTERVAL_MS) {
    tryCurrentWindow();
  }
  delay(5);
}
