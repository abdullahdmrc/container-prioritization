#define ENABLE_USER_AUTH
#define ENABLE_DATABASE

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <FirebaseClient.h>

#include <HardwareSerial.h>
#include <TinyGPS++.h>
#include "DHT.h"
#include "HX711.h"

// Network and Firebase credentials
#define WIFI_SSID "X"
#define WIFI_PASSWORD "12345678"

#define Web_API_KEY "YOUR_API.-KEY"
#define DATABASE_URL "YOUR_DATABASE_URL"
#define USER_EMAIL "X"
#define USER_PASS "123456"

#define DHTType DHT11

const int MQ4_ANALOG_PIN = 34;   // ESP32 analog pin (GPIO34)
#define RL_VALUE 5.0        // Yük direnci (kΩ) genelde 5kΩ olur
#define ADC_RESOLUTION 4095.0 // ESP32 için ADC çözünürlüğü
#define VOLTAGE_REF 3.3     // Referans voltaj (ESP32 için 3.3V)

// MQ-4'e özgü eğri sabitleri (datasheet üzerinden)
#define A -0.45
#define B 1.35

float R0 = 9.83; // Kalibrasyon sonucu bulunan R0.
#define DOUT 19
#define SCK 18
HX711 scale;
const int dstcTriggerPin = 27;
const int dstcEchoPin = 26;
const int dstcTriggerPin1 = 13;
const int dstcEchoPin1 = 12;
const int DHTPin = 21;

static const int RXPin = 16, TXPin = 17; // ESP32 Hardware Serial pinleri
static const uint32_t GPSBaud = 9600;    //  Neo-6M default baud rate

// Her 10 saniyede bir veri göndermek için zamanlayıcı değişkenleri
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 10000; // 10 saniye milisaniye cinsinden

// database e gönderilen veriler

TinyGPSPlus gps;
HardwareSerial gpsSerial(1); //  UART1 
DHT dht(DHTPin, DHTType);

// User function
void processData(AsyncResult &aResult);

// Authentication
UserAuth user_auth(Web_API_KEY, USER_EMAIL, USER_PASS);

// Firebase components
FirebaseApp app;
WiFiClientSecure ssl_client;
using AsyncClient = AsyncClientClass;
AsyncClient aClient(ssl_client);
RealtimeDatabase Database;

int fullnessRate;
float temperature;
unsigned long start;

double latitude = 0;
double longitude = 0;

double methanePPM = 0;
double weight = 0;

void readDistance(){
  long distance;
  long duration;
  long distance1;
  long duration1;

  digitalWrite(dstcTriggerPin,LOW);
  delayMicroseconds(5);
  digitalWrite(dstcTriggerPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(dstcTriggerPin, LOW);

  duration = pulseIn(dstcEchoPin, HIGH);
  if (duration == 0) distance = 30;
  else distance = duration / 58.2;
  if(distance > 30){
  	distance = 30;  
  }

  digitalWrite(dstcTriggerPin1,LOW);
  delayMicroseconds(5);
  digitalWrite(dstcTriggerPin1, HIGH);
  delayMicroseconds(10);
  digitalWrite(dstcTriggerPin1, LOW);

  duration1 = pulseIn(dstcEchoPin1, HIGH);
  if (duration1 == 0) distance1 = 30;
  else distance1 = duration1 / 58.2;
  if(distance1 > 30){
  	distance1 = 30;  
  }

  fullnessRate = (30 - ((distance + distance1)/2)) * 100 / 30;
  Serial.println(fullnessRate);
}

void readGPS(){
     
  if (gps.location.isUpdated()) {
    latitude = (gps.location.lat());
    Serial.print("Latitude: ");
    Serial.print(latitude);
    longitude = (gps.location.lng());
    Serial.print(" Longitude: ");
    Serial.print(longitude);
    Serial.print(" Speed: ");
    Serial.print(gps.speed.kmph());
    Serial.println(" km/h");
  }
  
}

void readTemperature(){
  temperature = dht.readTemperature();

  if(isnan(temperature)){
    Serial.println("Failed to read from DHT sensor!");
  }
  else{
    Serial.print("Temperature: ");
    Serial.println(temperature);
  }
}

void readWeight(){
  if (scale.is_ready()) {
    Serial.print("Reading: ");
    weight = scale.get_units(10);
    if(weight < 0){
      weight = weight * -1;
    }
    Serial.println(weight);
  } else {
    Serial.println("HX711 not connected");
  }
}

void readMethan(){
  int analogValue = analogRead(MQ4_ANALOG_PIN);
  float sensorVoltage = (analogValue / ADC_RESOLUTION) * VOLTAGE_REF;

  float RS = (VOLTAGE_REF - sensorVoltage) * RL_VALUE / sensorVoltage;

  float ratio = RS / R0;
  if (ratio <= 0) ratio = 0.01;  // log(0) hatası engeli

  float ppm = pow(10, (A * log10(ratio) + B));

  methanePPM = ppm; // Global değişkene ata

  Serial.print("Analog: ");
  Serial.print(analogValue);
  Serial.print("  | Voltaj: ");
  Serial.print(sensorVoltage, 2);
  Serial.print(" V  | RS: ");
  Serial.print(RS, 2);
  Serial.print(" kΩ  | PPM: ");
  Serial.println(ppm, 2);
}


void processData(AsyncResult &aResult) {
  if (!aResult.isResult())
    return;

  if (aResult.isEvent())
    Firebase.printf("Event task: %s, msg: %s, code: %d\n", aResult.uid().c_str(), aResult.eventLog().message().c_str(), aResult.eventLog().code());

  if (aResult.isDebug())
    Firebase.printf("Debug task: %s, msg: %s\n", aResult.uid().c_str(), aResult.debug().c_str());

  if (aResult.isError())
    Firebase.printf("Error task: %s, msg: %s, code: %d\n", aResult.uid().c_str(), aResult.error().message().c_str(), aResult.error().code());

  if (aResult.available())
    Firebase.printf("task: %s, payload: %s\n", aResult.uid().c_str(), aResult.c_str());
}

void sendData() {
  static int step = 0;

  // Check if Firebase App is ready and interval süresi geçti mi
  if (!app.ready()) return;

  unsigned long currentTime = millis();
  if (currentTime - lastSendTime < sendInterval) return;

  switch (step) {
    case 0:
      Database.set<int>(aClient, "/container1/fullnessRate", fullnessRate, processData, "RTDB_Send_Int");
      step++;
      break;
    case 1:
      Database.set<float>(aClient, "/container1/temperature", temperature, processData, "RTDB_Send_Float");
      step++;
      break;
    case 2:
      if(latitude != 0){
        Database.set<double>(aClient, "/container1/latitude", latitude, processData, "RTDB_Send_Double_Lat");
      }
      step++;
      break;
    case 3:
      if(longitude != 0){
        Database.set<double>(aClient, "/container1/longitude", longitude, processData, "RTDB_Send_Double_Lon");
      }
      step++;
      break;
    case 4:
      Database.set<double>(aClient, "/container1/methane", methanePPM, processData, "RTDB_Send_Double");
      step++;
      break;
    case 5:
      Database.set<double>(aClient, "/container1/weight", weight, processData, "RTDB_Send_Double");
      step = 0;
      lastSendTime = currentTime; // After all steps done update lastSendTime
      break;
  }
}

void setup() {
  // put your setup code here, to run once:
  pinMode(dstcTriggerPin, OUTPUT);
  pinMode(dstcEchoPin, INPUT);
  pinMode(dstcTriggerPin1, OUTPUT);
  pinMode(dstcEchoPin1, INPUT);
  // Start Serial 2 with the defined RX and TX pins and a baud rate of 9600
  Serial.begin(115200); // Monitor serial
  gpsSerial.begin(GPSBaud, SERIAL_8N1, RXPin, TXPin);
  Serial.println("GPS Module is starting...");
  dht.begin();
  scale.begin(DOUT, SCK);
  Serial.println("Remove all weight...");
  delay(3000);
  scale.tare();  // reset
  scale.set_scale(-16); // Arrange calibration factor
  // Connect to Wi-Fi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
  }
  Serial.println("Setting up time...");
  // NTP database
  configTime(3 * 3600, 0, "pool.ntp.org", "time.nist.gov");
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    Serial.println("Failed to obtain time");
    return; // dont continue if time not taken
  }
  Serial.println("Time synchronized");
   // Configure SSL client
  ssl_client.setInsecure();
  ssl_client.setConnectionTimeout(5000);
  ssl_client.setHandshakeTimeout(5);
  // Initialize Firebase
  initializeApp(aClient, app, getAuth(user_auth), processData, "🔐 authTask");
  app.getApp<RealtimeDatabase>(Database);
  Database.url(DATABASE_URL);
  start = millis();
}

void loop() {
  // put your main code here, to run repeatedly:
     // Maintain authentication and async tasks
  app.loop();

  while (gpsSerial.available()) {
    gps.encode(gpsSerial.read()); // Parse GPS data
  }
  if(millis() - start >= 5000){
    readDistance();
    readGPS();
    readTemperature();
    readMethan();
    readWeight();
    sendData();
    start = millis();
  }

  delay(100); 
}