package vertx.worker.traces;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static io.vertx.core.ThreadingModel.EVENT_LOOP;
import static io.vertx.core.ThreadingModel.WORKER;

@SuppressWarnings("ALL")
public final class WorkerTraceMain {
    private static final Logger log = LoggerFactory.getLogger("log-trace");

    private static final String WORKER_ADDRESS = "vertx.worker.address";
    private static final String WORKER_ADDRESS_ANSWER = "vertx.worker.address.answer";
    private static final int NET_PORT = 8081;

    private static Map<String, NetSocket> sockets = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        final var vertxOptions = new VertxOptions()
                .setTracingOptions(new OpenTelemetryOptions(OpenTelemetryConfig.configure()));

        final var vertx = Vertx.builder()
                .with(vertxOptions)
                .build();

        Future.join(
                        Stream.of(
                                new Particle(eventLoopVerticle(), EVENT_LOOP),
                                new Particle(eventLoopVerticle(), EVENT_LOOP),
                                new Particle(workerVerticle(), WORKER)
                        ).map(it -> vertx.deployVerticle(it.verticle, it.options)).toList()
                ).onSuccess(__ -> log.info("All verticles deployed successfully"))
                .onFailure(e -> {
                    log.error("Deployment failed: {}", e.getMessage());
                    System.exit(1);
                });

    }

    private static AbstractVerticle eventLoopVerticle() {
        return new AbstractVerticle() {
            @Override
            public void start(Promise<Void> startPromise) {
                vertx.eventBus().<JsonObject>consumer(WORKER_ADDRESS, msg -> busHandler(msg));
                startPromise.complete();
            }

            //@WithSpan
            private void busHandler(Message<JsonObject> msg) {
                new WithSpan("socketHandle", false).withSpan(__ -> {
                    final var body = msg.body();
                    final var id = body.getString("id");

                    final var json = new JsonObject()
                            .put("response", "Hello world!")
                            .put("id", id);
                    log.info("Processing request for {}", id);
                    vertx.eventBus().send(WORKER_ADDRESS_ANSWER, json,
                            new DeliveryOptions()
                                    .setTracingPolicy(TracingPolicy.PROPAGATE));
                });
            }
        };
    }

    private static AbstractVerticle workerVerticle() {
        return new AbstractVerticle() {
            @Override
            public void start(Promise<Void> startPromise) {
                final var netServer = vertx.createNetServer();

                netServer.connectHandler(socket -> {
                    socket.handler(buffer -> socketHandle(socket, buffer));
                    socket.closeHandler(v -> sockets.values().removeIf(s -> s == socket));
                });

                vertx.eventBus().<JsonObject>consumer(WORKER_ADDRESS_ANSWER, msg -> messageHandle(msg));

                netServer.listen(NET_PORT)
                        .onSuccess(v -> startPromise.complete())
                        .onFailure(startPromise::fail);
            }

            //@WithSpan
            private void socketHandle(NetSocket socket, Buffer buffer) {
                new WithSpan("socketHandle", false).withSpan(span -> {
                    final var request = new JsonObject(buffer.toString());
                    final var id = request.getString("id");

                    span.setAttribute("net.request.id", id);
                    log.info("Get request from NET for {}", id);

                    sockets.put(id, socket);

                    vertx.eventBus().send(
                            WORKER_ADDRESS,
                            request,
                            new DeliveryOptions().setTracingPolicy(TracingPolicy.PROPAGATE)
                    );
                });
            }

            //@WithSpan
            private void messageHandle(Message<JsonObject> msg) {
                new WithSpan("messageHandle", false).withSpan(span -> {
                    final var response = msg.body();
                    final var id = response.getString("id");
                    span.setAttribute("response.id", id);
                    final var socket = sockets.get(id);
                    if (socket != null) {
                        log.info("Send response to NET for: {}", id);
                        socket.write(response.encode());
                        socket.close();
                    } else {
                        log.warn("Socket not found for id: {}", id);
                    }
                });
            }
        };
    }


    record Particle(AbstractVerticle verticle, DeploymentOptions options) {
        Particle(AbstractVerticle verticle, ThreadingModel threadingModel) {
            this(verticle, new DeploymentOptions().setThreadingModel(threadingModel));
        }
    }
}
