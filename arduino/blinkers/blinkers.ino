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
CRGB colors[8] = {red, orange, yellow, green, cyan, blue, purple, purple};
CRGB selectedColors[8] = {blue, blue, blue, blue, blue, blue, blue, blue};

bool isSyncedWithPhase = false;
bool isSleepStarted = true;
unsigned long sleepStartTime;
unsigned long phaseStartTime;
unsigned long currentTime;
unsigned long cycleStartTime;
unsigned long alternatingStartTime;
int currentPhase = -1;
int colorCode = 0;
long phaseDuration = 0;
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
  cycleStartTime = millis();
}

void turnOffAll() {
  for (int i = 0; i < NUM_LEDS; i++) {
    leds[i] = CRGB::Black;
  }
  FastLED.show();
}

void incrementPhase() {
  turnOffAll();
  if (currentPhase < 8) {
    currentPhase++;
    phaseStartTime = millis();
    isSyncedWithPhase = false;
  } else {
    isSleepStarted = true;
    sleepStartTime = millis();
  }
  //    int brightness = 0;    // how bright the LED is
  //int fadeAmount = 5;    // how many points to fade the LED by
}

void performSynced() {
  if (!isSyncedWithPhase) {
    int left = 8 - currentPhase;
    int right = 16 - currentPhase;

    for (int i = 0; i < NUM_LEDS; i++) {
      if (i == left || i == right) {
        leds[i] = selectedColors[currentPhase - 1];
      } else leds[i] = CRGB::Black;
    }
    
    FastLED.show();
    FastLED.delay(1000 / UPDATES_PER_SECOND);

    isSyncedWithPhase = true;
  } else if (phaseDuration <= (currentTime - phaseStartTime)) {
    incrementPhase();
  }
}
//
//void performAlternating() {
//  long currentTime = millis();
//  if (!isSyncedWithPhase) {
//    alternatingStartTime = millis();
//    isSyncedWithPhase = true;
//  }
//
//  if (prepDuration <= (currentTime - phaseStartTime)) {
//    incrementPhase();
//  } else {
//    if (currentTime - alternatingStartTime > alternatingTime) {
//      isLeftOn = !isLeftOn;
//      alternatingStartTime = millis();
//    }
//
//    if (isLeftOn) {
//      leds[7] = CRGB::Black;
//      leds[15] = CRGB::Blue;
//    } else {
//      leds[15] = CRGB::Black;
//      leds[7] = CRGB::Blue;
//    }
//
//    FastLED.show();
//  }
//}

void loop() {
  while (Serial.available()) {
    byte byteInput = Serial.read();
    int input = (int)byteInput;
    if (input >= 100 && input < 160) {
      // phase duration instruction between 100 and 159
      phaseDuration = (long)(input - 99) * 10000;
      Serial.println("Phase duration set");
      Serial.println(phaseDuration);
    } else if (input >= 50 && input < 60) {
      // brightness between 50 -59
      brightness = (input - 49) * 10;
      Serial.println("Brightness set");
      Serial.println(brightness);
      FastLED.setBrightness(brightness);

    } else if (input >= 30 && input < 40) {
      // color code
      colorCode = input - 30;
      for (int i = 0; i < 8; i++) {
        if (colorCode < 9) {
          selectedColors[i] = colors[colorCode];
        } else {
          selectedColors[i] = colors[i];
        }
      }
      Serial.println("color code set");
      Serial.println(colorCode);
    } else if (input >= 10 && input < 18) {
      currentPhase = input - 10;
      Serial.println("startPhase set");
      Serial.println(currentPhase);

    } else if (input == 200) {
      //start
      isSyncedWithPhase = false;
      isSleepStarted = false;
      // currentPhase = 0;
      incrementPhase();
      Serial.println("STARTED");
    } else if (input == 201) {
      currentPhase = -1;
      turnOffAll();
      Serial.println("STOPPED");
    }
  }

  currentTime = millis();
  // if (currentPhase == 0) {
  //    performAlternating();
  //  } else
  //
  if (currentPhase <= 8 && currentPhase >= 0) {
    performSynced();
  } else {
    turnOffAll();
  }

  delay(10);
}
