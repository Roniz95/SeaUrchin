#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <ESP8266HTTPClient.h>
#include <ArduinoJson.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <EEPROM.h>


//----------------SERVER VARIABLES------------
const char* ssid = "TP-LINK_AP_D62E"; //SSID of the WiFi network.
const char* password = "02860145"; //wifi password of the ssid network.
const char* http_server = "192.168.1.42"; //http device server address.
const char* http_server_port = "8081";  //http device server port
const char* herd_password = "qwerty1234"; //password of the herd, leave it at it is for simulation purposes.
const char* mqtt_server = "192.168.1.42";  //MQTT server address
const char* channel_name = "devices_bus";

String url = "http://";
String macAddr = WiFi.macAddress();
String clientId = "sensorArray-";



//------------------SENSORS SETUP--------------
#define ONE_WIRE_BUS D1  //digital pin D1 of nodeMCU
#define TURB_ANALOG 2  //analog pin A0 of nodeMCU

//temp sensor
int n_temperature = 25;   //number of samples to average temperature
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);

//turbidity sensor
int n_turbidity = 25; // number of samples to average turbidity
int sensorValue = 0;
float voltage = 0.00;
float turbidity = 0.00;
float Vclear = 2.85; // Output voltage to calibrate (with clear water).
int buttoncalib = 2;  // The pin location of the push sensor for calibration. Connected to Ground
int pushcalib = 1;  // variable for pin D2 status.



//----------------MQTT SETUP------------
WiFiClient espClient;
PubSubClient client(espClient);
long lastMsg = 0;
long lastMsgRest = 0;
int value = 0;



//--------------CODE--------------------

//function to reset the microcontroller    
void(*resetFunc) (void) = 0;

void setup_wifi(){
  delay(10);
  randomSeed(micros());
  macAddr.replace(":", "");

  Serial.println();
  Serial.print("connecting to wifi network: ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

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
//this function simulate the reading of a sensor and return a pseudo random value inside (LOW, HI) range.
float simulateReading(float LO, float HI) {
  
  float reading = LO + static_cast <float> (rand()) /( static_cast <float> (RAND_MAX/(HI-LO)));
  return reading;
}
// the temperature sensor is read for n_turbidity times and an average is returned.
float readTemperatureSensor() {
  float sum = 0.00;
  for (int i = 0; i < n_temperature, i++;) {
    sum += sensors.getTempCByIndex(0);
    delay(40);
  }        
  return  (sum / n_temperature);
  
}
//get the calibration from the device, should be manually do when uploading the code.
void setupTurbiditySensor() {  
  EEPROM.get(0, Vclear);  // recovers the last Vclear calibration value stored in ROM.
  delay(3000);  // Pause for 3 seg
}
// the turbidity sensor is read for n_turbidity times and an average is returned.
float readTurbiditySensor() {
  float sum = 0.00;  
        for (int i=0; i < n_turbidity; i++){
        sum += analogRead(TURB_ANALOG); // read the input on analog pin 1 (turbidity sensor analog output)      
        delay(10);
        }       
    sensorValue = sum / n_turbidity;    // average the n values  
    voltage = sensorValue * (5.000 / 1023.000); // Convert analog (0-1023) to voltage (0 - 5V)
    turbidity = 100.00 - (voltage / Vclear) * 100.00; // as relative percentage; 0% = clear water; 
          
     

    return sensorValue;
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

void reconnect() {
  while (!client.connected()) {
    Serial.print("Connecting to MQTT server...");
    
    clientId = "ESP8266Client-";
    macAddr.replace(":", "");
    clientId += macAddr;
    // try to connect the client
    if (client.connect(clientId.c_str())) {
      String printLine = "   client " + clientId + " connected" + mqtt_server;
      Serial.println(printLine);
      String body = "device with ID = ";
      body += clientId;
      body += " connected to channel ";
      body += channel_name;
      client.publish(channel_name, "");
      //subscribe the device to the channel
      client.subscribe(channel_name);
    } else {
      Serial.print("unable to connect to MQTT channel, rc=");
      Serial.print(client.state());
      Serial.println("trying again in 5 seconds");
      delay(5000);
    }
  }
}

//notify the device server that the device is going offline.
void goOffline() {
  HTTPClient http;
  String requestUrl = url + "/devices/" + macAddr + "/goOffline";
  http.begin(requestUrl);
  int httpCode = http.PUT("");
  http.end();
  if(httpCode = 204) {
    Serial.print("server correctly handled offline request");
  } else {
    Serial.print("there was a problem with the offline request");
  }
}

//make the device sleep, it will stay in this state until a reset command is receive on MQTT.
void goToSleep() {
  goOffline();
  while(true) {
    delay(1000);
    Serial.println("sleeping...");
    client.loop();
  }
  
}

//this function read from the sensors and send the datagram to the deviceServer
void sendDatagram() {
  HTTPClient http;
  String requestUrl = url + "/devices/" + macAddr + "/datagram";
  StaticJsonDocument<JSON_OBJECT_SIZE(2)> JSONdocument;
  JSONdocument["temperature"] = simulateReading(25, 28);
  JSONdocument["turbidity"] = simulateReading(10, 60);
  char JSONmessageBuffer[300];
  serializeJsonPretty(JSONdocument, JSONmessageBuffer);
  http.begin(requestUrl);
  int httpCode = http.POST(JSONmessageBuffer);
  http.end();
  if(httpCode == 204) {
    Serial.println("datagram sent");
    Serial.println(JSONmessageBuffer);
  } else {
    Serial.println("it was impossible to send the datagram");
  }
}

//function called when  MQTT message is received
void callback(char* topic, byte* payload, unsigned int length){
  Serial.print("Message received [channel: ");
  Serial.print(topic);
  Serial.println("]");

  for (int i = 0; i < length; i++){
    Serial.print((char)payload[i]);
  }
  Serial.println();
  DynamicJsonDocument doc(length);
  deserializeJson(doc, payload, length);

  const char* action = doc["action"];
  String clientId_req = doc["clientId"];
  Serial.println(clientId_req);
  Serial.println(clientId);

  Serial.printf("New action received: ");
  if (strcmp(action, "sleep") == 0 & clientId.equals(clientId_req)){
    Serial.println("going to sleep");
    client.publish("devices_bus", "going to sleep...");
    goToSleep();
    
  }
  if(strcmp(action, "reset") == 0 & clientId_req.equals(clientId) ) {
    client.publish(channel_name, "resetting...");
    resetFunc();
  }
}

void setup() {
  Serial.begin(9600);
  setup_wifi();
  sensors.begin();
  craftUrl();
  client.setCallback(callback);
  client.setServer(mqtt_server, 1883);

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
  if(!client.connected()) reconnect();
  client.loop();
  long now = millis();
  if (now - lastMsgRest > 2000) {
    lastMsgRest = now;
    sendDatagram();
  }
  
  
}