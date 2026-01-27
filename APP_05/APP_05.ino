#include <Servo.h>
#include <SoftwareSerial.h>

#define BT Serial  // Disconnect pins 0 and 1 from the Arduino when uploading the code to the board
//pin 0 TX , pin 1 RX
Servo servos[2];
const int servoPins[2] = {5, 6};

int mode = -1;
bool isStopped = false;

float currentAngle = 90.0;
float breathPhase = 0.0;
unsigned long lastCoughTime = 0;
const unsigned long coughInterval = 8000;
bool isCoughing = false;

unsigned long lastCycleTime = 0;
int cycleStage = 0;
const int maxCycles = 3;
const int apneaDuration = 5000;

float currentAmplitude = 0.0;
float currentInhaleTime = 1000;
float currentExhaleTime = 1200;
unsigned long lastBreathTime = 0;

int breathDirection = 1;
int breathCycleCount = 0;
float lastPhaseValue = 0.0;


unsigned long lastAngleSent = 0;
int previousMode = -2;
int lastModeBeforePause = -1;

void setup() {
  for (int i = 0; i < 2; i++) {
    servos[i].attach(servoPins[i]);
    servos[i].write(currentAngle); // inicialize in 90 degrees
  }

  BT.begin(9600);
  Serial.begin(9600);

  Serial.println("System Ready Press MODE to start.");

  // ignore commands the first two sec
  unsigned long start = millis();
  while (millis() - start < 2000) {
    if (BT.available()) {
      BT.read(); // discard any received command
    }
  }
}

void loop() {
  receiveBluetoothCommand();

  if (mode == -1 || isStopped) return;

  unsigned long currentTime = millis();
  float deltaTime = (currentTime - lastBreathTime) / 1000.0;
  lastBreathTime = currentTime;

  switch (mode) {
    case 0: regularBreathing(deltaTime); break;
    case 1: rapidBreathing(deltaTime); break;
    case 2: slowBreathing(deltaTime); break;
    case 3: apnea(); break;
    case 4: updateWithCough(deltaTime); break;
    case 5: cheyneStokes(deltaTime); break;
    case 6: biot(deltaTime); break;
  }

  setAllServos(currentAngle);
}


void sendAngle(float angle) {
  if (millis() - lastAngleSent >= 100) { // every 100ms
    BT.print("ANGLE:");
    BT.println(angle, 2);
    lastAngleSent = millis();
  }
}

void sendEvent(const String &eventText) {
  BT.print("EVENT:");
  BT.println(eventText);
  Serial.print("EVENT:");
  Serial.println(eventText);
}

void sendMode(const String &modeName) {
  BT.print("MODE:");
  BT.println(modeName);
  Serial.print("MODE:");
  Serial.println(modeName);
}


void receiveBluetoothCommand() {
  if (BT.available()) {
    char command = BT.read();
    switch (command) {
      case 'R': mode = 0;updateModeParameters(); break; // Regular
      case 'F': mode = 1;updateModeParameters();break; // Fast
      case 'S': mode = 2;updateModeParameters(); break; // Slow
      case 'A': mode = 3;updateModeParameters(); break; // Apnea
      case 'T': mode = 4;updateModeParameters(); break; // Cough
      case 'C': mode = 5; updateModeParameters();break; // Cheyne-Stokes
      case 'B': mode = 6;updateModeParameters(); break; // Biot
      case 'K': 
      if (!isStopped) {
      lastModeBeforePause =mode;
      isStopped =true;
      sendMode("PAUSED");
     } else {
        isStopped =false;
      mode = lastModeBeforePause;
     updateModeParameters();
      }
     break;
    }
    
  }
}

void updateModeParameters() {
  String modeName = "";
  switch (mode) {
    case 0:
      currentAmplitude = 7.0;
      currentInhaleTime = 1000;
      currentExhaleTime = 1200;
      modeName = "Regular";
      break;
    case 1:
      currentAmplitude = 5.0;
      currentInhaleTime = 600;
      currentExhaleTime = 700;
      modeName = "Fast";
      break;
    case 2:
      currentAmplitude = 10.0;
      currentInhaleTime = 2000;
      currentExhaleTime = 2500;
      modeName = "Slow";
      break;
    case 3:
      modeName = "Apnea";
      break;
    case 4:
      modeName = "Cough";
      break;
    case 5:
      cycleStage = 0;
      lastCycleTime = millis();
      modeName = "Cheyne-Stokes";
      break;
    case 6:
      modeName = "Biot";
      break;
  }
  if (modeName != "") sendMode(modeName);
}


void setAllServos(float angle) {
  currentAngle = angle;
  for (int i = 0; i < 2; i++) {
    servos[i].write(i < 1 ? angle : 180 - angle);
  }
}


void regularBreathing(float deltaTime) {
  breathPhase += deltaTime * (2 * PI / ((currentInhaleTime + currentExhaleTime) / 1000));
  if (breathPhase > 2 * PI) breathPhase -= 2 * PI;

  float rawSin = sin(breathPhase);
  float skewedSin = rawSin >= 0 ? pow(rawSin, 0.7) : -pow(-rawSin, 1.5);
  currentAngle = 90 + currentAmplitude * skewedSin;

  sendAngle(currentAngle);
}

void rapidBreathing(float deltaTime) { regularBreathing(deltaTime); }
void slowBreathing(float deltaTime)  { regularBreathing(deltaTime); }

void apnea() {
  currentAngle = 90.0;
  sendAngle(currentAngle);
}

void updateWithCough(float deltaTime) {
  regularBreathing(deltaTime);

  if (breathPhase < lastPhaseValue) {
    breathCycleCount++;
    if (breathCycleCount >= 2) {
      breathCycleCount = 0;
      int randomValue = random(0, 50);
      if (randomValue < 20) {
        sendEvent("COUGH");
        setAllServos(currentAngle + 5);
        delay(150);
        setAllServos(currentAngle - 5);
        delay(250);
        setAllServos(currentAngle + 5);
        delay(150);
      }
    }
  }
  lastPhaseValue = breathPhase;
}



void cheyneStokes(float deltaTime) {
  static float currentCSAmplitude = 5.0;

  if (cycleStage == 0) {
    currentCSAmplitude += deltaTime * 0.7;
    if (currentCSAmplitude >= 10.0) {
      currentCSAmplitude = 10.0;
      cycleStage = 1;
    }
  } 
  else if (cycleStage == 1) {
    currentCSAmplitude -= deltaTime * 0.7;
    if (currentCSAmplitude <= 5.0) {
      currentCSAmplitude = 5.0;
      cycleStage = 2;
      lastCycleTime = millis();
      sendEvent("APNEA");
    }
  } 
  else if (cycleStage == 2) {
    currentAngle = 90.0;
    setAllServos(currentAngle);
    sendAngle(currentAngle);

    if (millis() - lastCycleTime >= apneaDuration) {
      cycleStage = 0;          
      breathPhase = 0.0;       
    }
    return; 
  }

  // regular breathing with variable amplitude
  breathPhase += deltaTime * (2 * PI / ((currentInhaleTime + currentExhaleTime) / 1000));
  if (breathPhase > 2 * PI) breathPhase -= 2 * PI;

  float sinValue = sin(breathPhase);
  currentAngle = 90 + currentCSAmplitude * sinValue;

  setAllServos(currentAngle);
  sendAngle(currentAngle);
}

void biot(float deltaTime) {
  static int breathCount = 0;
  static int breathsBeforeApnea = 0;
  static bool inApnea = false;
  static unsigned long apneaStart = 0;
  static unsigned long apneaDuration = 3000;
  static float lastPhase = 0.0;

  if (!inApnea) {
    regularBreathing(deltaTime);
    if (breathPhase < lastPhase) {
      breathCount++;
      if (breathsBeforeApnea == 0) {
        breathsBeforeApnea = random(3, 7);
      }
      if (breathCount >= breathsBeforeApnea) {
        inApnea = true;
        apneaStart = millis();
        apneaDuration = random(2000, 4000);
        sendEvent("APNEA");
        breathCount = 0;
        breathsBeforeApnea = 0;
      }
    }
    lastPhase = breathPhase;
  } else {
    currentAngle = 90.0;
    sendAngle(currentAngle);
    if (millis() - apneaStart > apneaDuration) {
      inApnea = false;
      breathPhase = 0.0;
    }
  }
}
