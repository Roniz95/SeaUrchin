

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.MySQLClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RestServer extends AbstractVerticle{
    private String herdPws = "qwerty1234";
    private DateFormat dateFormat;
    private Date dateAndTime;
    private int herdId = 1;  //id of the herd this server is handling;
    private AsyncSQLClient mySQLClient;

    public void start(Future<Void> startFuture) {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        JsonObject config = new JsonObject()
                .put("host", "localhost")
                .put("username", "root")
                .put("password", "Katerpillar95")
                .put("database", "seaUrchin")
                .put("port", 3306);
        mySQLClient = MySQLClient.createShared(vertx, config);



        Router router = Router.router(vertx);
        vertx.createHttpServer().requestHandler(router).
                listen(8081, result -> {
                    if (result.succeeded()) {
                        System.out.println("Servidor desplegado");
                    }else {
                        System.out.println("Error de despliegue");
                    }
                });
        router.route().handler(BodyHandler.create());
        router.get("/test").handler(this::handleTest);
        router.get("/test/DB_GET").handler(this::handleTestDB_GET);
        router.post("/test/DB_POST").handler(this::handleTestDB_POST);
        router.post("/devices/:macaddr/datagram").handler(this::handleDatagram_POST);
        router.post("/devices/:macaddr/register").handler(this::handleDeviceRegistration_POST);
    }
    //register a new datagram from a seaUrchin device to the server
    private void handleDeviceRegistration_POST(RoutingContext routingContext) {
        JsonObject jsonObject = routingContext.getBodyAsJson();
        //check if the device is allowed to register
        if(herdPws.equals(jsonObject.getString("password"))) {
            System.out.println("pinged");
            mySQLClient.getConnection(connection -> {
                if(connection.succeeded()){
                    String macAddr = routingContext.pathParam("macaddr");

                    JsonArray values = new JsonArray();
                    values.add(macAddr);
                    values.add(jsonObject.getValue("deviceType"));
                    values.add(herdId);

                    String preparedQuery = "INSERT INTO devices VALUES ( ?, ?, ?);";
                    connection.result().updateWithParams(preparedQuery, values, res -> {
                        if(res.succeeded()) {
                            routingContext.response().setStatusCode(204).end();
                        } else {
                            //the device is already registered
                            if(res.cause().getMessage().contains("Error 1062")) {
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
            if(connection.succeeded()){
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
                    if(res.succeeded()){
                        routingContext.response().setStatusCode(204).end();


                    } else {
                        routingContext.response().setStatusCode(400).end();
                    }
                });
            }else {
                System.out.println(connection.cause().getMessage());
                routingContext.response().setStatusCode(400).end();
            }
            connection.result().close();
        });
    }


    private void handleTestDB_POST(RoutingContext routingContext) {
        mySQLClient.getConnection(connection-> {
            if (connection.succeeded()) {
                JsonArray params = routingContext.getBodyAsJsonArray();
                String insert = "INSERT INTO test_table (name) VALUES (?);";

                connection.result().updateWithParams(insert, params, result -> {
                    if(result.succeeded()){
                        routingContext.response().setStatusCode(204).end();
                    }
                });
            } else {
                System.out.println(connection.cause().getMessage());
                routingContext.response().setStatusCode(400);
            }
        });
    }
    private void handleTestDB_GET(RoutingContext routingContext) {
        System.out.println("handling...");
        mySQLClient.getConnection(connection-> {
            if (connection.succeeded()) {
                connection.result().query("SELECT * FROM test_table", result -> {
                    if(result.succeeded()){
                        String jsonResult = new JsonArray(result.result().getResults()).encodePrettily();
                        routingContext.response().end(jsonResult);
                    }
                });
            } else {
                System.out.println(connection.cause().getMessage());
                routingContext.response().setStatusCode(400);
            }
        });
    }
    private void handleTest(RoutingContext routingContext) {
        System.out.println("we're getting pinged by: " + routingContext.request().remoteAddress().toString());

        JsonObject respJson = new JsonObject();
        respJson.put("name", "Sergio");
        respJson.put("surname", "Placanica");
        routingContext.response()
                .putHeader("content-type", "application/json")
                .end(respJson.encode());
    }

}
