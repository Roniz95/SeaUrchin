#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <ESP8266HTTPClient.h>
#include <ArduinoJson.h>
const char* ssid_2 ="wifi-huawei-lan";
const char* ssid = "TP-LINK_AP_D62E";
const char* password = "02860145";
const char* password_2 = "sergio1995";
const char* http_server = "192.168.43.226";
const char* http_server_port = "8081";
const char* herd_password = "qwerty1234";
// String clientId;

WiFiClient espClient;
PubSubClient client(espClient);
String macAddr = WiFi.macAddress();
long lastMsg = 0;
long lastMsgRest = 0;
char msg[50];
int value = 0;
String url = "http://";


//function to reset board    
void(*resetFunc) (void) = 0;

void setup_wifi(){
  delay(10);
  randomSeed(micros());
  macAddr.replace(":", "");

  Serial.println();
  Serial.print("connecting to wifi network: ");
  Serial.println(ssid_2);

  WiFi.begin(ssid_2, password_2);
  //attemp to connect to wifi
  while(WiFi.status() != WL_CONNECTED){
    Serial.print(".");
    delay(500);
  }
  
  Serial.println("");
  Serial.println("WiFi connected");
  Serial.print("current IP");
  Serial.println(WiFi.localIP());

  
}
void craftUrl() {
  url += http_server;
    url += ":";
    url += http_server_port;
}

bool registerDevice() {
  HTTPClient http;
  String requestUrl = url + "/devices/" + macAddr + "/register";
  Serial.println(requestUrl);
  StaticJsonDocument<JSON_OBJECT_SIZE(2)> JSONdocument;
  JSONdocument["deviceType"] = "sensorArray";
  JSONdocument["password"] = herd_password;
  char JSONmessageBuffer[300];
  serializeJsonPretty(JSONdocument, JSONmessageBuffer);

  http.begin(requestUrl);
  http.addHeader("Content-Type", "application/json");
  Serial.println(JSONmessageBuffer);
  int httpCode = http.POST(JSONmessageBuffer);
  Serial.println(httpCode);
  http.end();
  if(httpCode == 204 || httpCode == 409) return true;
  return false;
  
  
  

}
void sendDatagram() {
  HTTPClient http;
  String requestUrl = url + "/devices/" + macAddr + "/datagram";
  StaticJsonDocument<JSON_OBJECT_SIZE(2)> JSONdocument;
  JSONdocument["temperature"] = 37.5;
  JSONdocument["turbidity"] = 15;
  char JSONmessageBuffer[300];
  serializeJsonPretty(JSONdocument, JSONmessageBuffer);
  http.begin(requestUrl);
  int httpCode = http.POST(JSONmessageBuffer);
  http.end();
  if(httpCode == 204) {
    Serial.println("datagram sent");
    Serial.print(JSONmessageBuffer);
  } else {
    Serial.println("it was impossible to send the datagram");
  }
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

void goToSleep() {
  unsigned long counter = 0;
  while(counter <(60*60*4)) {
    delay(1000);
    Serial.print("sleeping...");
  }
  resetFunc();
}


void setup() {

  Serial.begin(9600);
  setup_wifi();
  craftUrl();
  client.setCallback(callback);

  //attempt to register device
  int attempts = 0;
  while(registerDevice() != true) {
     attempts++;
     delay(5000);
     if(attempts > 3) {
       Serial.println("it was impossible to register the device");
       goToSleep();
       
     }
  }
  Serial.println("device correctly connected to the herd");
}

void loop() {
  long now = millis();
  if (now - lastMsgRest > 2000) {
    lastMsgRest = now;
    sendDatagram();
  }
  delay(10000);
  
}