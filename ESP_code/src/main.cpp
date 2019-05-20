#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <ESP8266HTTPClient.h>
#include <ArduinoJson.h>

const char* ssid = "TP-LINK_AP_D62E";
const char* password = "02860145";
const char* http_server = "192.168.1.35";
const char* http_server_port = "8081";
// String clientId;

WiFiClient espClient;
PubSubClient client(espClient);
long lastMsg = 0;
long lastMsgRest = 0;
char msg[50];
int value = 0;

void setup_wifi(){
  delay(10);
  randomSeed(micros());

  Serial.println();
  Serial.print("connecting to wifi network: ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  while(WiFi.status() != WL_CONNECTED){
    Serial.print(".");
    delay(500);
  }

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.print("current IP");
  Serial.println(WiFi.localIP());
}

void callback(char* topic, byte* payload, unsigned int length){
  Serial.print("Mensage recibido [canal: ");
  Serial.print(topic);
  Serial.println("]");

  for (int i = 0; i < length; i++){
    Serial.print((char)payload[i]);
  }
  Serial.println();
  DynamicJsonDocument doc(length);
  deserializeJson(doc, payload, length);

  const char* action = doc["action"];
  const char* timestamp = doc["timestamp"];
  const char* clientId = doc["clientId"];

  Serial.printf("Nueva acciÃ³n recibida: %s\n", action);
  if (strcmp(action, "on") == 0){
    digitalWrite(BUILTIN_LED, HIGH);
    Serial.println("Enciendo luces");
  }else if (strcmp(action, "off") == 0){
    digitalWrite(BUILTIN_LED, LOW);
    Serial.println("Apagando luces");
  }else{
    Serial.println("AcciÃ³n no reconocida");
  }
}

void makeGetRequest(){
    HTTPClient http;
    // Abrimos la conexiÃ³n con el servidor REST y definimos la URL del recurso
    String url = "http://";
    url += http_server;
    url += ":";
    url += http_server_port;
    url += "/test";
    String message = "sending a test get request to server : ";
    message += url;
    Serial.println(message);
    http.begin(url);
    // Realizamos la peticiÃ³n y obtenemos el cÃ³digo de estado de la respuesta
    int httpCode = http.GET();

    if (httpCode > 0)
    {
     // Si el cÃ³digo devuelto es > 0, significa que tenemos respuesta, aunque
     // no necesariamente va a ser positivo (podrÃ­a ser un cÃ³digo 400).
     // Obtenemos el cuerpo de la respuesta y lo imprimimos por el puerto serie
     String payload = http.getString();
     Serial.println("payload: " + payload);

     const size_t bufferSize = JSON_OBJECT_SIZE(1) + 370;
     DynamicJsonDocument root(bufferSize);
     deserializeJson(root, payload);

     const char* surname = root["surname"];
     const char* name = root["name"];

     Serial.print("Name:");
     Serial.println(name);
     Serial.print("Surname:");
     Serial.println(surname);
    }

    Serial.printf("\nRespuesta servidor REST %d\n", httpCode);
    // Cerramos la conexiÃ³n con el servidor REST
    http.end();
}


void setup() {

  Serial.begin(9600);
  setup_wifi();
  client.setCallback(callback);
}

void loop() {
  long now = millis();
  if (now - lastMsgRest > 2000) {
    lastMsgRest = now;
    makeGetRequest();
  }
  delay(500);
  client.loop();
}