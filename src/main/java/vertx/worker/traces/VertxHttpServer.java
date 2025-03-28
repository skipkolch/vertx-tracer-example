package vertx.worker.traces;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VertxHttpServer extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger("http-server");

    private static final int HTTP_PORT = 8080;

    private static final String SOCKET_HOST = "localhost";
    private static final int SOCKET_PORT = 8081;

    private static final int SOCKET_TIMEOUT_MS = 5000;

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new VertxHttpServer());
    }

    @Override
    public void start(Promise<Void> startPromise) {
        NetClient netClient = vertx.createNetClient(
                new NetClientOptions()
                        .setConnectTimeout(SOCKET_TIMEOUT_MS)
                        .setReconnectAttempts(2)
        );

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.get("/api").handler(ctx -> {
            String id = ctx.request().getParam("id");

            if (id == null || id.trim().isEmpty()) {
                sendErrorResponse(ctx, 400, "id parameter is required");
                return;
            }

            JsonObject requestMessage = new JsonObject()
                    .put("id", id)
                    .put("request", ctx.request().uri());

            netClient.connect(SOCKET_PORT, SOCKET_HOST, res -> sendToSocket(ctx, res, id, requestMessage));
        });

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(HTTP_PORT)
                .onSuccess(server ->
                        log.info("Server started on port {}", HTTP_PORT))
                .onFailure(err ->
                        log.error("Server start failed: {}", err.getMessage()))
                .onComplete(__ -> startPromise.complete());
    }

    private void sendToSocket(RoutingContext ctx, AsyncResult<NetSocket> res, String id, JsonObject requestMessage) {
        if (res.failed()) {
            sendErrorResponse(ctx, 502, "Failed to connect to socket server: " + res.cause().getMessage());
            return;
        }

        NetSocket socket = res.result();
        Buffer responseBuffer = Buffer.buffer();

        socket.handler(responseBuffer::appendBuffer);

        socket.closeHandler(v -> {
            if (responseBuffer.length() > 0) {
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(responseBuffer);
            } else {
                sendErrorResponse(ctx, 504, "Socket connection closed without response");
            }
        });

        log.info("Send request to socket server for: {}", id);
        socket.write(requestMessage.encode() + "\n");
    }

    private void sendErrorResponse(RoutingContext ctx, int statusCode, String errorMessage) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                        .put("error", errorMessage)
                        .encode());
    }
}
