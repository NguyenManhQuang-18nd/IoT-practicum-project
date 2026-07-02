#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>
#include "DHT.h"

// --- CẤU HÌNH ---
#define AREA_NAME "Khu_bep_3" // Đổi tên cho đúng khu vực
#define WIFI_SSID "Wokwi-GUEST" 
#define WIFI_PASSWORD ""
#define API_KEY "AIzaSyCc6Rz4WLu3xXO0gP9g90G6VVhBSq5MpFg"
#define DATABASE_URL "https://tuan-8-c101c-default-rtdb.firebaseio.com"

// --- CHÂN LINH KIỆN ---
#define DHT_PIN 4
#define MQ2_PIN 34
#define WARNING_LED_PIN 25 
#define LIGHT_LED_PIN   26 

// --- CẤU HÌNH PWM (Core v2.x cho PlatformIO) ---
const int pwmFreq = 5000;
const int pwmChannel = 0;
const int pwmResolution = 8; 

DHT dht(DHT_PIN, DHT22);
FirebaseData fbdo_write;
FirebaseData fbdo_read;
FirebaseAuth auth;
FirebaseConfig config;
bool signupOK = false;

unsigned long sendDataPrevMillis = 0;
unsigned long readLightPrevMillis = 0;
int manualGas = -1; 

void setup() {
  Serial.begin(115200);
  dht.begin();
  
  pinMode(WARNING_LED_PIN, OUTPUT);
  digitalWrite(WARNING_LED_PIN, LOW);

  // Khởi tạo PWM
  ledcSetup(pwmChannel, pwmFreq, pwmResolution); 
  ledcAttachPin(LIGHT_LED_PIN, pwmChannel);
  ledcWrite(pwmChannel, 0); 

  Serial.print("Dang ket noi WiFi Wokwi...");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  Serial.println("\nWiFi OK!");

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;

  if (Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("Firebase dang nhap thanh cong!");
    signupOK = true;
  }
  
  config.token_status_callback = tokenStatusCallback;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
}

void loop() {
  // 1. NHẬP GAS THỦ CÔNG (TEST)
  if (Serial.available() > 0) {
    String input = Serial.readStringUntil('\n');
    input.trim();
    if (input.length() > 0) {
      manualGas = input.toInt();
      Serial.printf("\n[TEST] Ép Gas thành: %d\n", manualGas);
    }
  }

  if (Firebase.ready() && signupOK) {
    
    // 2. LUỒNG ĐIỀU KHIỂN ĐÈN
    if (millis() - readLightPrevMillis > 500) {
      readLightPrevMillis = millis();
      if (Firebase.RTDB.getInt(&fbdo_read, "/" + String(AREA_NAME) + "/den")) {
        int lightPercent = fbdo_read.intData(); 
        int pwmValue = map(lightPercent, 0, 100, 0, 255);
        ledcWrite(pwmChannel, pwmValue); 
      }
    }

    // 3. LUỒNG GỬI DỮ LIỆU CẢM BIẾN
    if (millis() - sendDataPrevMillis > 2000) {
      sendDataPrevMillis = millis();
      
      float h = dht.readHumidity();
      float t = dht.readTemperature();
      
      // --- THUẬT TOÁN ĐỔI PPM (PIECEWISE LINEAR) ---
      int rawGas = analogRead(MQ2_PIN);
      int gasPPM = 0;
      
      if (rawGas < 843) {
          gasPPM = 0;
      } 
      else if (rawGas <= 3337) {
          // Đoạn 1: Tương ứng 0.1 - 100 ppm
          gasPPM = map(rawGas, 843, 3337, 1, 100);
      } 
      else {
          // Đoạn 2: Tương ứng 100 - 1000 ppm
          gasPPM = map(rawGas, 3337, 3762, 100, 1000);
      }
      
      // Giới hạn an toàn
      if (gasPPM > 1000) gasPPM = 1000;
      
      int gasToSend = (manualGas >= 0) ? manualGas : gasPPM;

      // Cảnh báo cháy
      if (gasToSend > 400) digitalWrite(WARNING_LED_PIN, HIGH);
      else digitalWrite(WARNING_LED_PIN, LOW);

      FirebaseJson json;
      json.set("nhiet_do", t);
      json.set("do_am", h);
      json.set("khi_gas", gasToSend);
      
      if (Firebase.RTDB.updateNode(&fbdo_write, "/" + String(AREA_NAME), &json)) {
        Serial.printf("Gui -> Temp: %.1f | Hum: %.1f | Gas: %d PPM\n", t, h, gasToSend);
      }
    }
  }
}