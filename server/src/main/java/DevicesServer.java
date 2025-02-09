

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.MySQLClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;



import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

public class DevicesServer extends AbstractVerticle {
    private String herdPws = "qwerty1234";
    private DateFormat dateFormat;
    private Date dateAndTime;
    private int herdId = 1;  //id of the herd this server is handling;
    private AsyncSQLClient mySQLClient;
    private static Multimap<String, MqttEndpoint> clientTopics;
    private MqttClient mqttClient;

    public void start(Future<Void> startFuture) {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        MqttServer mqttServer = MqttServer.create(vertx);
        initMqttServer(mqttServer);
        clientTopics = HashMultimap.create();
        mqttClient = MqttClient.create(vertx, new MqttClientOptions()
                .setAutoKeepAlive(true)
                .setClientId("server_client"));
        mqttClient.connect(1883, "localhost", s -> {

            mqttClient.subscribe("devices_bus", MqttQoS.AT_LEAST_ONCE.value(), handler -> {
                if (handler.succeeded()) {
                    System.out.println("Client " + mqttClient.clientId() + "correctly subscribed");
                }
            });

        });



        JsonObject config = new JsonObject()
                .put("host", "localhost")
                .put("username", "root")
                .put("password", "*******")
                .put("database", "seaUrchin")
                .put("port", 3306);
        mySQLClient = MySQLClient.createShared(vertx, config);


        Router router = Router.router(vertx);
        vertx.createHttpServer().requestHandler(router).
                listen(8081, result -> {
                    if (result.succeeded()) {
                        System.out.println("Servidor desplegado");
                    } else {
                        System.out.println("Error de despliegue");
                    }
                });
        router.route().handler(BodyHandler.create());

        router.post("/devices/:macaddr/datagram").handler(this::handleDatagram_POST);
        router.post("/devices/:macaddr/register").handler(this::handleDeviceRegistration_POST);

        router.put("/devices/:macaddr/sleep").handler(this::handleDeviceSleep_PUT);
        router.put("/devices/:macaddr/reset").handler(this::handleDeviceReset_PUT);
        router.put("/devices/:macaddr/goOffline").handler(this::handleDeviceOffline_PUT);
        router.put("/devices/sleep").handler(this::handleDeviceSleepAll_PUT);

    }

    private void handleDeviceSleepAll_PUT(RoutingContext routingContext) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("action", "sleepAll");
        mqttClient.publish("devices_bus", Buffer.buffer(jsonObject.encodePrettily()), MqttQoS.AT_LEAST_ONCE, false, false);
        routingContext.response().end("sleepAll request sent");
    }

    private void handleDeviceOffline_PUT(RoutingContext routingContext) {
        mySQLClient.getConnection(connection -> {
           if(connection.succeeded()){
               String updateQuery = "UPDATE devices SET deviceStatus = 'offline' WHERE macAddr = ?;";
               JsonArray jsonArray = new JsonArray();
               jsonArray.add(routingContext.request().getParam("macaddr"));
               connection.result().queryWithParams(updateQuery, jsonArray, res -> {
                   if(res.succeeded()) {
                       routingContext.response().setStatusCode(204).end();

                   } else {
                       routingContext.response().setStatusCode(500).end("it was impossible to UPDATE the value");
                   }
               });
           } else {
                routingContext.response().setStatusCode(500).end("it was impossible to contact the DB");
           }
        });
        mySQLClient.close();
    }

    private void handleDeviceReset_PUT(RoutingContext routingContext) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("action", "reset");
        jsonObject.put("clientId","ESP8266Client-" + routingContext.request().getParam("macaddr"));
        mqttClient.publish("devices_bus", Buffer.buffer(jsonObject.encodePrettily()), MqttQoS.AT_LEAST_ONCE, false, false);
        routingContext.response().end("reset request sent");
    }

    private void handleDeviceSleep_PUT(RoutingContext routingContext) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("action", "sleep");
        jsonObject.put("clientId", "ESP8266Client-" + routingContext.request().getParam("macaddr"));
        mqttClient.publish("devices_bus", Buffer.buffer(jsonObject.encodePrettily()), MqttQoS.AT_LEAST_ONCE, false, false);
        routingContext.response().end("sleep request sent");
    }


    //register a new datagram from a seaUrchin device to the server
    private void handleDeviceRegistration_POST(RoutingContext routingContext) {
        JsonObject jsonObject = routingContext.getBodyAsJson();
        //check if the device is allowed to register
        if (herdPws.equals(jsonObject.getString("password"))) {
            mySQLClient.getConnection(connection -> {
                if (connection.succeeded()) {
                    String macAddr = routingContext.pathParam("macaddr");

                    JsonArray values = new JsonArray();
                    values.add(macAddr);
                    values.add(jsonObject.getValue("deviceType"));
                    values.add(herdId);

                    String insPreparedQuery = "INSERT INTO devices VALUES ( ?, ?, ?, 'online');";
                    connection.result().updateWithParams(insPreparedQuery, values, res -> {
                        if (res.succeeded()) {
                            routingContext.response().setStatusCode(204).end();
                        } else {
                            //the device is already registered
                            if (res.cause().getMessage().contains("Error 1062")) {
                                String setOnlineQuery = "UPDATE devices SET deviceStatus = 'online' WHERE macAddr = ?;";
                                JsonArray jsonArray = new JsonArray();
                                jsonArray.add(macAddr);
                                mySQLClient.updateWithParams(setOnlineQuery, jsonArray, res2 -> {
                                    if(res2.succeeded()) {
                                        //do nothing
                                    }else {

                                    }
                                });
                                routingContext.response().setStatusCode(409).end(res.cause().getMessage());
                            }
                            routingContext.response().setStatusCode(400);
                            System.out.println(res.cause().getMessage());
                        }
                    });

                } else {
                    routingContext.response().setStatusCode(400).end();
                }
                connection.result().close();
            });
        }

    }

    //send a new datagram to the server
    private void handleDatagram_POST(RoutingContext routingContext) {
        mySQLClient.getConnection(connection -> {
            if (connection.succeeded()) {
                String macAddr = routingContext.pathParam("macaddr");
                String recordTime = dateFormat.format(new Date());
                JsonObject datagram = routingContext.getBodyAsJson();
                JsonArray postData = new JsonArray();
                postData.add(macAddr);
                postData.add(herdId);
                postData.add(recordTime);
                postData.add(datagram.getValue("temperature"));
                postData.add(datagram.getValue("turbidity"));
                System.out.println(postData);
                String preparedQuery = "INSERT INTO datagram VALUES (?, ?, ?, ?, ?);";
                connection.result().updateWithParams(preparedQuery, postData, res -> {
                    if (res.succeeded()) {
                        routingContext.response().setStatusCode(204).end();


                    } else {
                        routingContext.response().setStatusCode(400).end();
                    }
                });
            } else {
                System.out.println(connection.cause().getMessage());
                routingContext.response().setStatusCode(400).end();
            }
            connection.result().close();
        });
    }

    private static void initMqttServer(MqttServer mqttServer) {
        mqttServer.endpointHandler(endpoint -> {
            System.out.println("new MQTT client : " + endpoint.clientIdentifier());
            endpoint.accept(false);
            handleSubMqtt(endpoint);
            handleUnsubMqtt(endpoint);
            publishHandlerMqtt(endpoint);
            handleClientDiscMqtt(endpoint);

        }).listen(ar -> {
            if(ar.succeeded()) {
                System.out.println("MQTT server listening on port: " + ar.result().actualPort());
            } else {
                System.out.println("error in MQTT server deployment");
                ar.cause().printStackTrace();
            }
        });
    }

    private static void handleSubMqtt(MqttEndpoint endpoint) {
        endpoint.subscribeHandler(sub -> {
            List<MqttQoS> grantedQosLevels = new ArrayList<>();
            for (MqttTopicSubscription s : sub.topicSubscriptions()) {
                System.out.println("subscribing to" + s.topicName() + " with QoS " + s.qualityOfService());
                grantedQosLevels.add(s.qualityOfService());


                clientTopics.put(s.topicName(), endpoint);
            }
        });
    }

    private static void handleUnsubMqtt(MqttEndpoint endpoint) {
        endpoint.unsubscribeHandler(unsub -> {
            for (String t : unsub.topics()) {
                //eliminate client from the list
                clientTopics.remove(t, endpoint);
                System.out.println("the device was unsubscribed : " + t);
            }
            // tell the client the unsub went good
            endpoint.unsubscribeAcknowledge(unsub.messageId());
        });
    }


    private static void handleClientDiscMqtt(MqttEndpoint endpoint) {
        endpoint.disconnectHandler(h -> {
            // the client is eliminated from the list of topics it was subscribed to
            Stream.of(clientTopics.keySet()).filter(e -> clientTopics.containsEntry(e, endpoint))
                    .forEach(s -> clientTopics.remove(s, endpoint));
            System.out.println("the remote client disconnected [" + endpoint.clientIdentifier() + "]");
        });
    }

    private static void publishHandlerMqtt(MqttEndpoint endpoint) {
        endpoint.publishHandler(message -> {
            handleMessage(message, endpoint);
        }).publishReleaseHandler(messageId -> {
            endpoint.publishComplete(messageId);
        });
    }

    private static void handleMessage(MqttPublishMessage message, MqttEndpoint endpoint) {
        System.out.println("Message published by " + endpoint.clientIdentifier() + " on the channel "
                + message.topicName());
        System.out.println("message : " + message.payload().toString());
        System.out.println("Origin: " + endpoint.clientIdentifier());
        for (MqttEndpoint client : clientTopics.get(message.topicName())) {
            System.out.println("Destination: " + client.clientIdentifier());
            if (!client.clientIdentifier().equals(endpoint.clientIdentifier()))
                try {
                    client.publish(message.topicName(), message.payload(), message.qosLevel(), message.isDup(),
                            message.isRetain()).publishReleaseHandler(idHandler -> {
                        client.publishComplete(idHandler);
                    });
                } catch (Exception e) {
                    System.out.println("Error, can't send the message");
                }
        }

        if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
            String topicName = message.topicName();
            switch (topicName) {

            }
            endpoint.publishAcknowledge(message.messageId());
        } else if (message.qosLevel() == MqttQoS.EXACTLY_ONCE) {
            endpoint.publishRelease(message.messageId());
        }
    }




}
