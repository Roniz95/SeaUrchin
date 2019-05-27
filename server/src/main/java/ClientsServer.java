import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.MySQLClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;
import io.vertx.ext.web.api.validation.ParameterType;
import io.vertx.ext.web.api.validation.ValidationException;
import io.vertx.ext.web.handler.BodyHandler;

public class ClientsServer extends AbstractVerticle {
    private AsyncSQLClient mySQLClient;

    private HTTPRequestValidationHandler validationHandlerGetDatagrams = HTTPRequestValidationHandler
            .create()
            .addQueryParam("macAddr", ParameterType.GENERIC_STRING, false)
            .addQueryParam("fromDate", ParameterType.DATE, false)
            .addQueryParam("toDate", ParameterType.DATE, false);

    public  void start(Future<Void> Future){
        JsonObject config = new JsonObject()
                .put("host", "localhost")
                .put("username", "root")
                .put("password", "Katerpillar95")
                .put("database", "seaUrchin")
                .put("port", 3306);
        mySQLClient = MySQLClient.createShared(vertx, config);

        Router router = Router.router(vertx);
        vertx.createHttpServer().requestHandler(router).
                listen(8082, result -> {
                    if (result.succeeded()) {
                        System.out.println("Clients Server online");
                    }else {
                        System.out.println("Clients server cannot be started");
                    }
                });
        router.route().handler(BodyHandler.create());
        router.errorHandler(400, routingContext -> {
            if (routingContext.failure() instanceof ValidationException) {
                // Something went wrong during validation!
                String validationErrorMessage = routingContext.failure().getMessage();
            } else {
                // Unknown 400 failure happened
                routingContext.response().setStatusCode(400).end();
            }
        });

        router.get("/herds/:herdsId/datagrams/*")
                .handler(validationHandlerGetDatagrams)
                .handler(this::handleDatagramReqFiltered_GET)
                .failureHandler((routingContext) -> {
                    Throwable failure = routingContext.failure();
                    if (failure instanceof ValidationException) {
                        // Something went wrong during validation!
                        String validationErrorMessage = failure.getMessage();
                        System.out.println(validationErrorMessage);
                    }
                });

        router.get("/herd/:herdId/datagrams").handler(this::handleDatagramReqAll_GET);
        
    }
    //TODO handle bag parameters in url
    private void handleDatagramReqAll_GET(RoutingContext routingContext) {
        mySQLClient.getConnection(connection -> {
           if(connection.succeeded()){
               connection
                       .result()
                       .query("SELECT * FROM datagram ORDER BY recordTime, deviceMacAddr;",
                               res -> {
                                   if (res.succeeded()) {
                                       JsonObject queryResult = new JsonObject();
                                       queryResult.put("numberOfRows", res.result().getNumRows());
                                       queryResult.put("datagrams", res.result().getRows());
                                       routingContext.response()
                                               .putHeader("Content-Type", "application/json")
                                               .setStatusCode(200)
                                               .end(queryResult.encodePrettily());
                                   } else {
                                       System.out.println(res.cause().getMessage());
                                       routingContext
                                               .response()
                                               .setStatusCode(400)
                                               .end();
                                   }
                               });
           } else {
               System.out.println(connection.cause().getMessage());
               routingContext
                       .response()
                       .setStatusCode(404)
                       .end();
           }
        });
        mySQLClient.close();
    }

    //TODO handle bad parameters in url


    //return a JsonObject with "numberOfRows" and "datagrams"
    private void handleDatagramReqFiltered_GET(RoutingContext routingContext) {
        RequestParameters params = routingContext.get("parsedParameters");
        JsonArray queryParametersJson = new JsonArray();
        mySQLClient.getConnection(connection -> {
            if(connection.succeeded()) {

                String preparedQuery = "SELECT * FROM datagram WHERE";
                if(params.queryParameter("macAddr") != null) {

                    System.out.println(params.queryParameter("macAddr").getString());
                    preparedQuery += " deviceMacAddr = ?";
                    queryParametersJson.add(params.queryParameter("macAddr").getString());
                }

                if(params.queryParameter("fromDate")!= null) {
                    if(!queryParametersJson.isEmpty()) preparedQuery += " AND";
                    preparedQuery += " recordTime > ?";
                    queryParametersJson.add(params.queryParameter("fromDate").getString());
                }
                if(params.queryParameter("toDate") != null) {
                    if (!queryParametersJson.isEmpty()) preparedQuery += " AND";
                    preparedQuery +=" recordTime < ?";
                    queryParametersJson.add(params.queryParameter("toDate").getString());
                }
                preparedQuery += " ORDER BY deviceMacAddr, recordTime;";
                System.out.println(preparedQuery);
                mySQLClient.queryWithParams(preparedQuery, queryParametersJson, res -> {
                    if (res.succeeded()) {
                        JsonObject resultOfQuery = new JsonObject();
                        resultOfQuery.put("numberOfRows", res.result().getNumRows());
                        resultOfQuery.put("datagrams", res.result().getRows());
                        routingContext.
                                response().
                                putHeader("Content-Type", "application/json")
                                .end(resultOfQuery.encodePrettily());


                    } else {
                        System.out.println(res.cause().getMessage());
                        routingContext
                                .response()
                                .putHeader("Content-Type", "application/json")
                                .setStatusCode(400)
                                .end();
                    }

                });
            } else {
                    routingContext.response().setStatusCode(404).end();
            }
            mySQLClient.close();
        });

    }

}
