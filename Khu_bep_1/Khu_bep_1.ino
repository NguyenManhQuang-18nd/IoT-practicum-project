#include <Arduino.h>
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>
#include "DHT.h"

#define DHT_PIN         4
#define DHT_TYPE        DHT22
#define MQ2_PIN         34
#define WARNING_LED_PIN 25
#define LIGHT_LED_PIN   26

DHT dht(DHT_PIN, DHT_TYPE);
const int pwmFreq = 5000;
const int pwmResolution = 8; 

#define WIFI_SSID "...123" 
#define WIFI_PASSWORD "123456789"
#define API_KEY "AIzaSyCc6Rz4WLu3xXO0gP9g90G6VVhBSq5MpFg"
#define DATABASE_URL "https://tuan-8-c101c-default-rtdb.firebaseio.com"

FirebaseData fbdo_read;
FirebaseData fbdo_write;
FirebaseAuth auth;
FirebaseConfig config;
bool signupOK = false;

unsigned long sendDataPrevMillis = 0;
unsigned long readLightPrevMillis = 0;
unsigned long dhtPrevMillis = 0;
unsigned long ledAlarmStartTime = 0;

const long sendInterval = 1000;
const long readInterval = 500;
const long dhtInterval = 2000;
const unsigned long LED_HOLD_TIME = 10000; 

bool isAlarmActive = false;
float currentTemp = 30.0; 
float currentHum = 50.0;

void setup() {
  Serial.begin(115200);
  delay(1000); // CHỜ SERIAL ỔN ĐỊNH
  dht.begin();
  
  pinMode(WARNING_LED_PIN, OUTPUT);
  digitalWrite(WARNING_LED_PIN, LOW);
  
  ledcAttach(LIGHT_LED_PIN, pwmFreq, pwmResolution);
  ledcWrite(LIGHT_LED_PIN, 0); 
  
  analogReadResolution(12);
  analogSetPinAttenuation(MQ2_PIN, ADC_11db);

  Serial.println("Dang ket noi Wi-Fi...");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(300);
  }
  Serial.println("\nKet noi Wi-Fi thanh cong!");

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;

  if (Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("Firebase dang nhap an danh thanh cong");
    signupOK = true;
  }
  
  config.token_status_callback = tokenStatusCallback;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
}

void loop() {
  if (Firebase.ready() && signupOK) {
    
    // --- LUỒNG 1: ĐÈN ---
    if (millis() - readLightPrevMillis > readInterval) {
      readLightPrevMillis = millis();
      if (Firebase.RTDB.getInt(&fbdo_read, "/Khu_bep_1/den")) {
        ledcWrite(LIGHT_LED_PIN, map(fbdo_read.intData(), 0, 100, 0, 255)); 
      }
    }

    // --- LUỒNG 2: DHT22 ---
    if (millis() - dhtPrevMillis > dhtInterval) {
      dhtPrevMillis = millis();
      float t = dht.readTemperature();
      float h = dht.readHumidity();
      if (!isnan(t) && !isnan(h)) {
        currentTemp = t;
        currentHum = h;
      }
    }

    // --- LUỒNG 3: BƠM DỮ LIỆU & LOGIC CẢNH BÁO ---
    if (millis() - sendDataPrevMillis > sendInterval) {
      sendDataPrevMillis = millis();
      int gasRaw = analogRead(MQ2_PIN);

      // Logic Cảnh báo
      if (gasRaw >= 800) {
        ledAlarmStartTime = millis(); 
        isAlarmActive = true;
      }

      if (isAlarmActive) {
        digitalWrite(WARNING_LED_PIN, HIGH);
        if (millis() - ledAlarmStartTime >= LED_HOLD_TIME) {
          digitalWrite(WARNING_LED_PIN, LOW);
          isAlarmActive = false;
        }
      }

      FirebaseJson json;
      json.set("nhiet_do", currentTemp);
      json.set("do_am", currentHum);       
      json.set("khi_gas", gasRaw);    
      
      if (Firebase.RTDB.updateNode(&fbdo_write, "/Khu_bep_1", &json)) {
         Serial.printf("Gui -> Temp: %.1f | Hum: %.1f | Butane: %d | LED: %s\n", 
                        currentTemp, currentHum, gasRaw, isAlarmActive ? "ON" : "OFF");
      } else {
         Serial.println(fbdo_write.errorReason());
      }
    }
  }
}