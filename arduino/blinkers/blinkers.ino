#include <FastLED.h>
#define NUM_LEDS 16
#define DATA_PIN 2
#define LED_TYPE    WS2812
#define COLOR_ORDER GRB
#define UPDATES_PER_SECOND 100
CRGB leds[NUM_LEDS];
TBlendType blending = LINEARBLEND;
#define UPDATES_PER_SECOND 100
CRGB red  = CHSV( HUE_RED, 255, 255);
CRGB orange  = CHSV( HUE_ORANGE, 255, 255);
CRGB yellow  = CHSV( HUE_YELLOW, 255, 255);
CRGB green  = CHSV( HUE_GREEN, 255, 255);
CRGB cyan  = CHSV( HUE_AQUA, 255, 255);
CRGB blue  = CHSV( HUE_BLUE, 255, 255);
CRGB purple = CHSV( HUE_PURPLE, 255, 255);
CRGB white  = CHSV( 255, 255, 255);
CRGB selectedPalette[7];

bool isSynced = false;
bool isSleepStarted = true;
unsigned long sleepStartTime;
unsigned long phaseStartTime;
unsigned long colorStartTime;
unsigned long currentTime;

int currentPhase = -1;
int currentColorIndex = 0;
int endPhase = 0;
long phaseDuration = 0;
long colorDuration = 0;
int colorCount = 0;
int colorCode = 0;
long sessionDuration = 0;
long repeatTime = 0;
const int alternatingTime = 1000;
bool isLeftOn = true;
int brightness = 80;
String readString;

void setup() {
  delay(3000);
  Serial.begin(115200);
  FastLED.addLeds<LED_TYPE, DATA_PIN, COLOR_ORDER>(leds, NUM_LEDS).setCorrection(TypicalLEDStrip);
  FastLED.setBrightness(50);
}

void turnOffAll() {
  for (int i = 0; i < NUM_LEDS; i++) {
    leds[i] = CRGB::Black;
  }
  FastLED.show();
}

void incrementPhase() {
  turnOffAll();
  if (currentPhase <= endPhase) {
    currentPhase++;
    phaseStartTime = millis();
    isSynced = false;
  } else {
    isSleepStarted = true;
    sleepStartTime = millis();
  }
}

void incrementColor() {
  if (currentColorIndex < colorCount - 1) {
    currentColorIndex++;
    colorStartTime = millis();
    isSynced = false;
  }
}

void perform() {
  if (!isSynced) {
    int left = 8 - currentPhase;
    int right = 16 - currentPhase;

    for (int i = 0; i < NUM_LEDS; i++) {
      if (i == left || i == right) {
        leds[i] = selectedPalette[currentColorIndex];
      } else leds[i] = CRGB::Black;
    }

    FastLED.show();
    FastLED.delay(1000 / UPDATES_PER_SECOND);

    isSynced = true;
  } else {
    if (phaseDuration <= (currentTime - phaseStartTime)) {
      incrementPhase();
    }
    if (colorDuration <= (currentTime - colorStartTime)) {
      incrementColor();
    }
  }
}

void setPalette() {
        switch (colorCode) {
        case 0:
          selectedPalette[0] = red;
          selectedPalette[1] = orange;
          selectedPalette[2] = yellow;
          colorCount = 3;
          Serial.println("Fire palette selected");
          break;
        case 1:
          selectedPalette[0] = green;
          selectedPalette[1] = cyan;
          selectedPalette[2] = purple;
          selectedPalette[3] = blue;
          colorCount = 4;
          Serial.println("Water palette selected");
          break;
        case 2:
          selectedPalette[2] = orange;
          selectedPalette[1] = yellow;
          selectedPalette[0] = green;
          colorCount = 3;
          Serial.println("Earth palette selected");
          break;
        case 3:
          selectedPalette[0] = blue;
          selectedPalette[1] = cyan;
          colorCount = 2;
          Serial.println("Air palette selected");
          break;
        case 4:
        default:
          selectedPalette[0] = red;
          selectedPalette[1] = orange;
          selectedPalette[2] = yellow;
          selectedPalette[3] = green;
          selectedPalette[4] = cyan;
          selectedPalette[5] = blue;
          selectedPalette[6] = purple;
          colorCount = 7;
          Serial.println("Rainbow palette selected");
          break;
      }
}

void checkSerial() {
  while (Serial.available()) {
    byte byteInput = Serial.read();
    int input = (int)byteInput;
    if (input > 100 && input < 160) {
      // phase duration instruction between 100 and 159
      sessionDuration = (long)(input - 100) * 60 * 1000;
      Serial.println("Session duration set");
      Serial.println(sessionDuration);
    } else if (input >= 50 && input < 60) {
      // brightness between 50 -59
      brightness = (input - 49) * 10;
      Serial.println("Brightness set");
      Serial.println(brightness);
      FastLED.setBrightness(brightness);

    } else if (input >= 30 && input < 35) {
      // color code
      colorCode = input - 30;
      setPalette();
    } else if (input >= 10 && input < 18) {
      currentPhase = input - 10;
      Serial.println("startPhase set");
      Serial.println(currentPhase);
    } else if (input >= 20 && input < 28) {
      endPhase = input - 20;
      Serial.println("endPhase set");
      Serial.println(endPhase);
    } else if (input == 200) {
      //start
      phaseDuration = sessionDuration / (endPhase - currentPhase + 1);
      Serial.println("phaseDuration set");
      Serial.println(phaseDuration);

      colorDuration = sessionDuration / colorCount;
      Serial.println("colorDuration set");
      Serial.println(colorDuration);
      currentColorIndex = 0;
      colorStartTime = millis();
      isSynced = false;
      isSleepStarted = false;
      incrementPhase();
      Serial.println("STARTED");
    } else if (input == 201) {
      currentPhase = -1;
      turnOffAll();
      Serial.println("STOPPED");
    }
  }


}

void loop() {
  checkSerial();
  currentTime = millis();
  if (currentPhase <= 8 && currentPhase >= 0) {
    perform();
  } else {
    turnOffAll();
  }

  delay(10);
}
