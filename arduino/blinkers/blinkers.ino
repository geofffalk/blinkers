#include <FastLED.h>
#define NUM_LEDS 16
#define DATA_PIN 2
CRGB leds[NUM_LEDS];

bool isSyncedWithPhase = false;
bool isSleepStarted = false;
unsigned long sleepStartTime;
unsigned long phaseStartTime;
unsigned long currentTime;
unsigned long cycleStartTime;
unsigned long alternatingStartTime;
int currentPhase = -1;
int inputCount = 0;
long phaseTimes[] = {0, 0, 0, 0};
long repeatTime = 0;
const int alternatingTime = 1000;
bool isLeftOn = true;
String readString;

void setup() {
  delay(2000);
  FastLED.addLeds<WS2812, DATA_PIN, RGB>(leds, NUM_LEDS);
  cycleStartTime = millis();
}

void incrementPhase() {
  for (int i = 0; i < NUM_LEDS; i++) {
    leds[i] = CRGB::Black;
  }
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
    int left = (currentPhase - 1) % 8;
    int right = ((currentPhase - 1) % 8) + 8;

    for (int i = 0; i < NUM_LEDS; i++) {
      if (i == left || i == right) {
        leds[i] = CRGB::White;
      } else leds[i] = CRGB::Black;
    }

    FastLED.show();

    isSyncedWithPhase = true;
  } else if (phaseTimes[currentPhase] <= (currentTime - phaseStartTime)) {
    incrementPhase();
  }
}

void performAlternating() {
  long currentTime = millis();
  if (!isSyncedWithPhase) {
    alternatingStartTime = millis();
    isSyncedWithPhase = true;
  }

  if (phaseTimes[currentPhase] <= (currentTime - phaseStartTime)) {
    incrementPhase();
  } else {

    if (currentTime - alternatingStartTime > alternatingTime) {
      isLeftOn = !isLeftOn;
      alternatingStartTime = millis();
    }

    if (isLeftOn) {
      leds[7] = CRGB::Black;
      leds[15] = CRGB::White;
    } else {
      leds[15] = CRGB::Black;
      leds[7] = CRGB::White;
    }

  FastLED.show();
  }
}

void loop() {

  if (Serial.available()) {
    byte input = Serial.read();

    if (inputCount < 4) {
      phaseTimes[inputCount] = (long)input * 1000;
      inputCount++;
    } else {
      repeatTime = (long)input * 60 * 1000;
      inputCount = 0;
      isSyncedWithPhase = false;
      isSleepStarted = false;
      currentPhase = -1;
      incrementPhase();

      Serial.println("Phase times");
      Serial.println(phaseTimes[0]);
      Serial.println(phaseTimes[1]);
      Serial.println(phaseTimes[2]);
      Serial.println(phaseTimes[3]);
      Serial.println("Repeat time");
      Serial.println(repeatTime);
    }
  }

  currentTime = millis();

  if (isSleepStarted) {
    if (repeatTime != 0 && currentTime - sleepStartTime > repeatTime) {
      isSleepStarted = false;
      currentPhase = -1;
    }
  } else if (phaseTimes[currentPhase] == 0) {
    incrementPhase();
  } else if (currentPhase == 0) {
    performAlternating();
  } else if (currentPhase <= 3) {
    performSynced();
  }

  delay(10);
}
