

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Launcher;

public class Main extends AbstractVerticle {

    public static void main(final String[] args) {
        Launcher.executeCommand("run", Main.class.getName());
    }
    public void start(Future<Void> startFuture) {
        vertx.deployVerticle(new RestServer());

    }

}
