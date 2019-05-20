

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.MySQLClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class RestServer extends AbstractVerticle{

    private AsyncSQLClient mySQLClient;

    public void start(Future<Void> startFuture) {

        JsonObject config = new JsonObject()
                .put("host", "localhost")
                .put("username", "root")
                .put("password", "root")
                .put("database", "dad_db")
                .put("port", 3306);
        mySQLClient =
                MySQLClient.createShared(vertx, config);



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
        router.put("/products/:productID/:property").handler(this::handleProductProperty);

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

    private void handleAllSensors(RoutingContext routingContext) {
        mySQLClient.getConnection(connection -> {
            if (connection.succeeded()) {
                connection.result().query("SELECT * FROM sensor" , result -> {
                    if (result.succeeded()) {
                        String jsonResult = new JsonArray(result.result().getResults()).encodePrettily();
                        routingContext.response().end(jsonResult);
                    }else {
                        System.out.println(result.cause().getMessage());
                        routingContext.response().setStatusCode(400).end();
                    }
                });
            }else {
                System.out.println(connection.cause().getMessage());
                routingContext.response().setStatusCode(400).end();
            }
        });
    }

    private void handleProduct(RoutingContext routingContext) {
        String paramStr = routingContext.pathParam("productID");
        int paramInt = Integer.parseInt(paramStr);
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("serial", "asfas234ewsdcdwe24");
        jsonObject.put("id", paramInt);
        jsonObject.put("name", "TV Samsung");
        routingContext.response()
                .putHeader("content-type", "application/json")
                .end(jsonObject.encode());
    }

    private void handleProductProperty(RoutingContext routingContext) {
        String paramStr = routingContext.pathParam("productID");
        int paramInt = Integer.parseInt(paramStr);
        JsonObject body = routingContext.getBodyAsJson();
        // Petición BBDD
        routingContext.response()
                .putHeader("content-type", "application/json")
                .end(body.encode());
    }

}
