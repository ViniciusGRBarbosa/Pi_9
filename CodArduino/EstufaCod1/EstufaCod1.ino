#include <Arduino.h>
#if defined(ESP32) || defined(ARDUINO_RASPBERRY_PI_PICO_W)
#include <WiFi.h>
#elif defined(ESP8266)
#include <ESP8266WiFi.h>
#elif __has_include(<WiFiNINA.h>)
#include <WiFiNINA.h>
#elif __has_include(<WiFi101.h>)
#include <WiFi101.h>
#elif __has_include(<WiFiS3.h>)
#include <WiFiS3.h>
#endif

#include <Firebase_ESP_Client.h>

// Provide the token generation process info.
#include <addons/TokenHelper.h>

// Provide the RTDB payload printing info and other helper functions.
#include <addons/RTDBHelper.h>

#include <DHT.h>

/* 1. Define the WiFi credentials */
#define WIFI_SSID "S23 Ultra de Vinicius Gabriel"
#define WIFI_PASSWORD "pcgkkdr9y54r5qn"

// For the following credentials, see examples/Authentications/SignInAsUser/EmailPassword/EmailPassword.ino

/* 2. Define the API Key */
#define API_KEY "AIzaSyAAEwhSzDPPdEHiomo_lDa1PUBdq5kjm4Q"

/* 3. Define the RTDB URL */
#define DATABASE_URL "https://projeto-interdisciplinar-9-default-rtdb.firebaseio.com/"

/* 4. Define the user Email and password that already registered or added in your project */
#define USER_EMAIL "projetoint7@gmail.com"
#define USER_PASSWORD "Senha@123456"

// Define Firebase Data object
FirebaseData fbdo;

FirebaseAuth auth;
FirebaseConfig config;

unsigned long lastSensorDataSentTime = 0;
unsigned long lastCoolerCheckTime = 0;
unsigned long lastLampCheckTime = 0;
unsigned long sensorDataInterval = 12000; // Intervalo de envio dos dados dos sensores (2 minutos em milissegundos)
unsigned long coolerCheckInterval = 1000; // Intervalo de verificação da variável cooler (1 segundo em milissegundos)
unsigned long lampCheckInterval = 1000; // Intervalo de verificação da variável lâmpada (1 segundo em milissegundos)

#if defined(ARDUINO_RASPBERRY_PI_PICO_W)
WiFiMulti multi;
#endif

// DHT Sensor
#define DHTPIN D4     // Pino digital conectado ao sensor DHT
#define LAMP D0        // Pino digital conectado à lâmpada
#define vent D3         // Pino digital do ventilador
#define DHTTYPE DHT22  // Defina o tipo de sensor DHT (DHT11, DHT22, etc.)
DHT dht(DHTPIN, DHTTYPE);

// Soil Moisture Sensor
#define SOILPIN D6     // Pino digital conectado ao sensor de umidade do solo

// LDR Sensor
#define LDRPIN A0      // Pino analógico conectado ao sensor LDR

void setup()
{
  Serial.begin(115200);

#if defined(ARDUINO_RASPBERRY_PI_PICO_W)
  multi.addAP(WIFI_SSID, WIFI_PASSWORD);
  multi.run();
#else
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
#endif

  Serial.print("Connecting to Wi-Fi");
  unsigned long ms = millis();
  while (WiFi.status() != WL_CONNECTED)
  {
    Serial.print(".");
    delay(300);
#if defined(ARDUINO_RASPBERRY_PI_PICO_W)
    if (millis() - ms > 10000)
      break;
#endif
  }
  Serial.println();
  Serial.print("Connected with IP: ");
  Serial.println(WiFi.localIP());
  Serial.println();

  Serial.printf("Firebase Client v%s\n\n", FIREBASE_CLIENT_VERSION);

  /* Assign the api key (required) */
  config.api_key = API_KEY;

  /* Assign the user sign in credentials */
  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;

  /* Assign the RTDB URL (required) */
  config.database_url = DATABASE_URL;

  /* Assign the callback function for the long running token generation task */
  config.token_status_callback = tokenStatusCallback; // see addons/TokenHelper.h

  // Comment or pass false value when WiFi reconnection will control by your code or third party library e.g. WiFiManager
  Firebase.reconnectNetwork(true);

  // Since v4.4.x, BearSSL engine was used, the SSL buffer need to be set.
  // Large data transmission may require larger RX buffer, otherwise connection issue or data read time out can be occurred.
  fbdo.setBSSLBufferSize(4096 /* Rx buffer size in bytes from 512 - 16384 */, 1024 /* Tx buffer size in bytes from 512 - 16384 */);

  // Limit the size of response payload to be collected in FirebaseData
  fbdo.setResponseSize(2048);

  Firebase.begin(&config, &auth);

  // The WiFi credentials are required for Pico W
  // due to it does not have reconnect feature.
#if defined(ARDUINO_RASPBERRY_PI_PICO_W)
  config.wifi.clearAP();
  config.wifi.addAP(WIFI_SSID, WIFI_PASSWORD);
#endif

  Firebase.setDoubleDigits(5);

  config.timeout.serverResponse = 10 * 1000;

  // Initialize DHT sensor.
  dht.begin();

  // Set pin modes
  pinMode(LAMP, OUTPUT);
  pinMode(vent, OUTPUT);
}

void loop()
{
  // Verifica se a conexão com o Firebase está pronta e se é hora de enviar dados
  if (Firebase.ready() && (millis() - lastSensorDataSentTime > sensorDataInterval || lastSensorDataSentTime == 0))
  {
    lastSensorDataSentTime = millis();

    // Leitura do sensor de umidade do solo
    int soilSensorValue = digitalRead(SOILPIN); // Leitura do valor digital do sensor de umidade do solo
    float soilMoisture = soilSensorValue == HIGH ? 100.0 : 0.0; // Assume umidade de 100% se o pino digital estiver HIGH, caso contrário 0%

    // Leitura do sensor DHT
    float humidity = dht.readHumidity();    // Umidade do ar
    float temperature = dht.readTemperature(); // Temperatura em Celsius

    // Verifica se as leituras do DHT são válidas
    if (isnan(humidity) || isnan(temperature)) {
      Serial.println("Falha na leitura do sensor DHT!");
      return;
    }

    // Leitura do sensor de luminosidade LDR
    int ldrValue = analogRead(LDRPIN); // Leitura do valor analógico do sensor LDR
    float lightLevel = map(ldrValue, 0, 1023, 0, 100); // Mapeia o valor do sensor para um nível de luminosidade percentual

    // Cria objetos FirebaseJson para armazenar os dados a serem enviados
    FirebaseJson jsonHumidity, jsonTemperature, jsonSoilMoisture, jsonLightLevel;

    // Adiciona os dados dos sensores aos objetos FirebaseJson
    jsonHumidity.set(String(millis()), humidity);
    jsonTemperature.set(String(millis()), temperature);
    jsonSoilMoisture.set(String(millis()), soilMoisture);
    jsonLightLevel.set(String(millis()), lightLevel);

    // Define os caminhos no banco de dados onde os dados serão armazenados
    String humidityPath = "/sensor_data/humidity";
    String temperaturePath = "/sensor_data/temperature";
    String soilMoisturePath = "/sensor_data/soil_moisture";
    String lightLevelPath = "/sensor_data/light_level";

    // Envia os dados para o banco de dados
    Serial.printf("Sending humidity data to database... %s\n", Firebase.RTDB.updateNode(&fbdo, humidityPath, &jsonHumidity) ? "ok" : fbdo.errorReason().c_str());
    Serial.printf("Sending temperature data to database... %s\n", Firebase.RTDB.updateNode(&fbdo, temperaturePath, &jsonTemperature) ? "ok" : fbdo.errorReason().c_str());
    Serial.printf("Sending soil moisture data to database... %s\n", Firebase.RTDB.updateNode(&fbdo, soilMoisturePath, &jsonSoilMoisture) ? "ok" : fbdo.errorReason().c_str());
    Serial.printf("Sending light level data to database... %s\n", Firebase.RTDB.updateNode(&fbdo, lightLevelPath, &jsonLightLevel) ? "ok" : fbdo.errorReason().c_str());
  }

  // Verifica se é hora de verificar o estado da variável cooler
  if (millis() - lastCoolerCheckTime > coolerCheckInterval || lastCoolerCheckTime == 0)
  {
    lastCoolerCheckTime = millis();

    // Controle do ventilador através da variável Firebase "cooler"
    if (Firebase.RTDB.getInt(&fbdo, "/cooler"))
    {
      int coolerStatus = fbdo.intData();
      if (coolerStatus == 1)
      {
        digitalWrite(vent, HIGH); // Liga o ventilador
      }
      else
      {
        digitalWrite(vent, LOW);  // Desliga o ventilador
      }
    }
    else
    {
      Serial.printf("Erro ao ler a variável cooler: %s\n", fbdo.errorReason().c_str());
    }
  }

  // Verifica se é hora de verificar o estado da variável lâmpada
  if (millis() - lastLampCheckTime > lampCheckInterval || lastLampCheckTime == 0)
  {
    lastLampCheckTime = millis();

    // Controle da lâmpada através da variável Firebase "lampada"
    if (Firebase.RTDB.getInt(&fbdo, "/lampada"))
    {
      int lampStatus = fbdo.intData();
      if (lampStatus == 1)
      {
        digitalWrite(LAMP, HIGH); // Liga a lâmpada
      }
      else
      {
        digitalWrite(LAMP, LOW);  // Desliga a lâmpada
      }
    }
    else
    {
      Serial.printf("Erro ao ler a variável lampada: %s\n", fbdo.errorReason().c_str());
    }
  }
}
