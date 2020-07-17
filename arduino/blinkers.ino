
const int RED_L = 2;
const int RED_R = 3;
const int AMBER_L = 4;
const int AMBER_R = 5;
const int BLUE_L = 6;
const int BLUE_R = 7;
const int BUTTON = 12;

int buttonState = 0;
int brightness = 0;    // how bright the LED is
int fadeAmount = 5;    // how many points to fade the LED by
bool isSyncedPhaseStarted = false;
bool isSleepStarted = false;
unsigned long sleepStartTime;
unsigned long fullyLitStartTime;
unsigned long currentTime;
unsigned long cycleStartTime;
long currentPhase = 4;
const int phaseLedsLeft[] = { RED_L, AMBER_L, BLUE_L };
const int phaseLedsRight[] = { RED_R, AMBER_R, BLUE_R };
long phaseTimes[] = {0, 0, 0, 0};
long repeatTime = 0;
const int LED_TOTAL = 6;
const int allLeds[LED_TOTAL] = { RED_R, AMBER_R, BLUE_R, RED_L, AMBER_L, BLUE_L };
int alternatingTime = 100;
int alternatingCount = 0;
long alternatingStartTime;
bool isAlternatingStarted = false;
unsigned const long phaseTime = 30000;
bool isLeftOn = true;
String readString;



void setup() {
  Serial.begin(9600);
  pinMode(BLUE_R, OUTPUT);
  pinMode(BLUE_L, OUTPUT);
  pinMode(AMBER_R, OUTPUT);
  pinMode(AMBER_L, OUTPUT);
  pinMode(RED_R, OUTPUT);
  pinMode(RED_L, OUTPUT);
  pinMode(BUTTON, INPUT);
  cycleStartTime = millis();
}

void incrementPhase() {
  if (currentPhase < 3) {
    currentPhase++;
  } else {
    analogWrite(BLUE_L, 0);
    analogWrite(BLUE_R, 0);
    isSleepStarted = true;
    sleepStartTime = millis();
  }
  //    int brightness = 0;    // how bright the LED is
  //int fadeAmount = 5;    // how many points to fade the LED by
}

void performSynced() {
  int ledLeft = phaseLedsLeft[(currentPhase - 1) % 3];
  int ledRight = phaseLedsRight[(currentPhase - 1) % 3];

  for (int i = 0; i < LED_TOTAL; i++) {
    if (allLeds[i] == ledLeft || allLeds[i] == ledRight) {
      analogWrite(allLeds[i], brightness);
    } else {
      analogWrite(allLeds[i], 0);
    }
  }
  analogWrite(ledLeft, brightness);
  analogWrite(ledRight, brightness);

  if (!isSyncedPhaseStarted) {
    brightness = brightness + fadeAmount;
  }

  if (brightness >= 255) {
    if (!isSyncedPhaseStarted) {
      isSyncedPhaseStarted = true;
      fullyLitStartTime = millis();
    } else if (phaseTimes[currentPhase] <= (currentTime - fullyLitStartTime)) {
      isSyncedPhaseStarted = false;
      fadeAmount = -fadeAmount;
    }
  } else if (brightness <= 0) {
    fadeAmount = -fadeAmount;
    incrementPhase();
  }
}

void performAlternating() {
  long currentTime = millis();
  if (!isAlternatingStarted) {
    isAlternatingStarted = true;
    alternatingStartTime = millis();
  } else if (currentTime - alternatingStartTime > phaseTimes[0]) {
    incrementPhase();
    isAlternatingStarted = false;
  } else {
    if (alternatingCount > alternatingTime) {
      isLeftOn = !isLeftOn;
      alternatingCount = 0;
    }
    if (isLeftOn) {
      analogWrite(RED_L, 255);
      analogWrite(RED_R, 0);
    } else {
      analogWrite(RED_L, 0);
      analogWrite(RED_R, 255);
    }
    alternatingCount ++;
  }
}

void loop() {

  int newButtonState = digitalRead(BUTTON);

  if (newButtonState == HIGH && buttonState == LOW) {
    if (currentPhase > 0) {
      currentPhase--;
      isAlternatingStarted = false;
      isSyncedPhaseStarted = false;
      brightness = 0;
      fadeAmount = 5;
    }
    buttonState = newButtonState;
  } else buttonState = newButtonState;

  if (Serial.available()) {
    char c = Serial.read();
    if (c == '*') {
      Serial.println();
      Serial.print("captured String is : ");
      Serial.println(readString); //prints string to serial port out

      int ind1 = readString.indexOf(',');
      phaseTimes[0] = readString.substring(0, ind1).toInt();
      int ind2 = readString.indexOf(',', ind1 + 1 ); //finds location of second ,
      phaseTimes[1] = readString.substring(ind1 + 1, ind2 + 1).toInt(); //captures second data String
      int ind3 = readString.indexOf(',', ind2 + 1 );
      phaseTimes[2] = readString.substring(ind2 + 1, ind3 + 1).toInt();
      int ind4 = readString.indexOf(',', ind3 + 1 );
      phaseTimes[3] = readString.substring(ind3 + 1, ind4 + 1).toInt(); //captures remain part of data after last ,
      int ind5 = readString.indexOf(',', ind4 + 1 );
      repeatTime = readString.substring(ind4 + 1).toInt(); //captures remain part of data after last ,
      Serial.println("Phase times");
      Serial.println(phaseTimes[0]);
      Serial.println(phaseTimes[1]);
      Serial.println(phaseTimes[2]);
      Serial.println(phaseTimes[3]);
      Serial.println("Repeat time");
      Serial.println(repeatTime);

      readString = ""; //clears variable for new input

      isAlternatingStarted = false;
      isSyncedPhaseStarted = false;
      brightness = 0;
      fadeAmount = 5;
      isSleepStarted = false;

      currentPhase = 0;
    } else {
      readString += c; //makes the string readString
    }
  }

  currentTime = millis();

  if (isSleepStarted) {
    if (repeatTime != 0 && currentTime - sleepStartTime > repeatTime) {
      isSleepStarted = false;
      currentPhase = 0;
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
