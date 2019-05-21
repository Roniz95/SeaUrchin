import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.AsyncSQLClient;
import io.vertx.ext.asyncsql.MySQLClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class RestServerDB extends AbstractVerticle {
private AsyncSQLClient mySQLClient;

    public void start(Future<Void> startFuture) {
        JsonObject config = new JsonObject()
                .put("host", "localhost")
                .put("username", "rest_server")
                .put("password", "seaUrchin")
                .put("database", "test_DB")
                .put("port", 3306);
        mySQLClient =
                MySQLClient.createShared(vertx, config);

    }

}
