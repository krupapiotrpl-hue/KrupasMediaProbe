/*

  ============================================================

  KRUPAS OPEL DISPLAY v1.9f TEST EXPIRY

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

// Paired with APK 0.6.2: the same title renews this lease every 2 seconds.
const uint32_t TITLE_LEASE_MS = 8000;
const uint32_t CLEAR_RETRY_MS = 350;
const uint32_t CLEAR_DEADLINE_MS = 1500;
uint32_t lastTitleRx = 0;
bool clearPending = false;
bool clearAttempted = false;
uint32_t clearStartedAt = 0;
uint32_t lastClearAttempt = 0;



String lastTitleText = "";





// ============================================================

// DISPLAY

// ============================================================



const uint8_t DISPLAY_CHARS = 12;



const uint32_t SCROLL_INTERVAL_MS = 300;
const uint32_t SCROLL_START_PAUSE_MS = 1200;
const uint32_t SCROLL_END_PAUSE_MS = 900;



const uint32_t REFRESH_INTERVAL_MS = 700;



const uint32_t RETRY_INTERVAL_MS = 350;



const uint32_t FLOW_CONTROL_TIMEOUT_MS = 150;





uint16_t scrollPosition = 0;



uint32_t lastSuccessfulTx = 0;



uint32_t lastAttempt = 0;



uint32_t lastScroll = 0;



bool lastTxWasSuccessful = false;





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

    Serial.println(

      "CAN A BLAD"

    );



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



void getDisplayWindow(

  uint16_t *window

)

{

  // Zwykłe spacje.

  // Bez eksperymentów z U+00A0.



  for (

    uint8_t i = 0;

    i < DISPLAY_CHARS;

    i++

  )

  {

    window[i] =

      ' ';

  }





  if (

    titleLength == 0

  )

  {

    return;

  }





  // ----------------------------------------------------------

  // KRÓTKI TITLE

  // ----------------------------------------------------------



  if (

    titleLength <=

    DISPLAY_CHARS

  )

  {

    for (

      uint16_t i = 0;

      i < titleLength;

      i++

    )

    {

      window[i] =

        titleChars[i];

    }





    return;

  }





  // ----------------------------------------------------------

  // DŁUGI TITLE

  // ----------------------------------------------------------



  uint16_t virtualLength =

    titleLength + 3;





  for (

    uint8_t i = 0;

    i < DISPLAY_CHARS;

    i++

  )

  {

    uint16_t index =

      (scrollPosition + i)

      %

      virtualLength;





    if (

      index <

      titleLength

    )

    {

      window[i] =

        titleChars[index];

    }



    else

    {

      window[i] =

        ' ';

    }

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

    0x00, 0x67,

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



bool sendDisplayWindow(

  const uint16_t *window

)

{

  uint8_t payload[52];





  buildPayload(

    window,

    payload

  );





  // ----------------------------------------------------------

  // FIRST FRAME

  // ----------------------------------------------------------



  uint8_t ff[8];





  ff[0] =

    0x10;





  ff[1] =

    0x34;





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

    sizeof(payload)

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

      sizeof(payload) -

      offset;





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

      offset < sizeof(payload)

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

  if (

    titleLength == 0

  )

  {

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
    lastScroll = lastSuccessfulTx;





    lastTxWasSuccessful =

      true;





    Serial.print(

      "DISPLAY OK: ["

    );





    for (

      uint8_t i = 0;

      i < DISPLAY_CHARS;

      i++

    )

    {

      printUnicodeChar(

        window[i]

      );

    }





    Serial.println(

      "]"

    );

  }



  else

  {

    lastTxWasSuccessful =

      false;





    Serial.println(

      "DISPLAY BRAK FC"

    );

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





    Serial.print(

      "BLE RX UTF8: "

    );





    Serial.println(

      received

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





    Serial.println(

      "BLE POLACZONO"

    );

  }





  void onDisconnect(

    BLEServer *server

  )

  {

    bleConnected =

      false;





    Serial.println(

      "BLE ROZLACZONO"

    );





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





  BLEDevice::setMTU(517);

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





  Serial.println(

    "BLE OK: ZAFIRA-DISPLAY"

  );

}





// ============================================================

// TITLE Z BLE

// ============================================================



// Send a blank only while relinquishing our own previously active text.
// Repeated CLEAR writes never extend the retry deadline or blank a native screen.
void requestDisplayClear()
{
  if (titleLength == 0) return;
  titleLength = 0;
  lastTitleText = "";
  scrollPosition = 0;
  lastTxWasSuccessful = false;
  clearPending = true;
  clearAttempted = false;
  clearStartedAt = millis();
}

void serviceDisplayExpiry()
{
  uint32_t now = millis();
  if (titleLength > 0 && (uint32_t)(now - lastTitleRx) >= TITLE_LEASE_MS) {
    requestDisplayClear();
  }
  if (!clearPending) return;
  if ((uint32_t)(now - clearStartedAt) >= CLEAR_DEADLINE_MS) {
    clearPending = false;
    Serial.println("CLEAR timeout; releasing display without further CAN writes");
    return;
  }
  if (clearAttempted && (uint32_t)(now - lastClearAttempt) < CLEAR_RETRY_MS) return;
  clearAttempted = true;
  lastClearAttempt = now;
  uint16_t blank[DISPLAY_CHARS];
  for (uint8_t i = 0; i < DISPLAY_CHARS; ++i) blank[i] = ' ';
  if (sendDisplayWindow(blank)) {
    clearPending = false;
    Serial.println("DISPLAY CLEARED; periodic refresh stopped");
  }
}
void loadTitle(

  const String &text

)

{
  String checked = text;
  checked.trim();
  if (checked.length() == 0) {
    requestDisplayClear();
    return;
  }
  lastTitleRx = millis();
  clearPending = false;


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

    Serial.println(

      "TEN SAM TITLE - SCROLL BEZ RESETU"

    );





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





  Serial.print(

    "NOWY TITLE: "

  );





  for (

    uint16_t i = 0;

    i < titleLength;

    i++

  )

  {

    printUnicodeChar(

      titleChars[i]

    );

  }





  Serial.println();





  Serial.print(

    "LICZBA ZNAKOW: "

  );





  Serial.println(

    titleLength

  );





  tryCurrentWindow();

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





  Serial.println();



  Serial.println(

    "========================================"

  );



  Serial.println(

    "KRUPAS OPEL DISPLAY v1.9f TEST EXPIRY"

  );



  Serial.println(

    "LAST WORKING BASE"

  );



  Serial.println(

    "LEFT ALIGN = 0x6C"

  );



  Serial.println(

    "NO TITLE SCROLL RESET"

  );



  Serial.println(

    "CAN A ONLY"

  );



  Serial.println(

    "========================================"

  );





  if (

    initCAN()

  )

  {

    Serial.println(

      "CAN A OK"

    );

  }



  else

  {

    Serial.println(

      "CAN A BLAD"

    );

  }





  initBLE();





  Serial.println(

    "GOTOWE"

  );

}





// ============================================================

// LOOP

// ============================================================



void loop()

{

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

      loadTitle(

        temp

      );

    }

  }





  // ==========================================================

  // BRAK TITLE

  // ==========================================================



  serviceDisplayExpiry();

  if (

    titleLength == 0

  )

  {

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

    DISPLAY_CHARS

  )

  {

    if (

      millis() - lastScroll >=

      (scrollPosition == 0 ? SCROLL_START_PAUSE_MS :
       (scrollPosition == titleLength - DISPLAY_CHARS ? SCROLL_END_PAUSE_MS : SCROLL_INTERVAL_MS))
    )

    {

      uint16_t virtualLength =

        titleLength + 3;





      // Keep a failed target window pending; do not resend an older window.





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



      // On failure the RETRY block resends this same window.





      delay(5);



      return;

    }

  }





  // ==========================================================

  // PODTRZYMANIE

  // ==========================================================



  if (
    titleLength <= DISPLAY_CHARS &&
    millis() - lastSuccessfulTx >=
    REFRESH_INTERVAL_MS
  )
  {
    tryCurrentWindow();
  }





  delay(5);

}
