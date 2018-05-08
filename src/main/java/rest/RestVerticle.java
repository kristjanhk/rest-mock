package rest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import static rest.Utils.*;

/**
 * @author <a href="https://github.com/kristjanhk">Kristjan Hendrik Küngas</a>
 */
@Slf4j
public class RestVerticle extends AbstractVerticle {
  private static final long POLL_TIME = TimeUnit.SECONDS.toMillis(1);
  private static final Path DIR = Paths.get(System.getProperty("user.dir"));
  private static final Pattern PATTERN =
      Pattern.compile("(https?://)?(?<host>.*(\\.(.*){2,4})?):(?<port>\\d{3,5})(?<prefix>(/.*)*)");
  private static final String URL = "url";
  private static final String HTTP_CODE = "http_code";
  private static final String HTTP_METHOD = "http_method";
  private static final String RESPONSE = "response";

  private final WatchService watchService = FileSystems.getDefault().newWatchService();
  private final String host;
  private final int port;
  private final String prefix;
  private final Router router;

  private HttpServer server;
  private Map<String, Mock> mocksByFileName = new ConcurrentHashMap<>();
  private Map<String, Route> routesByUrl = new ConcurrentHashMap<>();

  private RestVerticle(String host, String port, String prefix) throws IOException {
    this.host = host;
    this.port = Integer.parseInt(port);
    this.prefix = prefix;
    this.router = Router.router(vertx);
    this.router.route().handler(BodyHandler.create());
    addLastRoute();
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      throw new IllegalArgumentException("Provide url argument. Use format: host:port");
    }
    if (args.length != 1) {
      throw new IllegalArgumentException(String.format("Invalid arguments %s. Use url format: host:port",
                                                       Arrays.toString(args)));
    }
    String url = args[0];
    Matcher matcher = PATTERN.matcher(url);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(String.format("Invalid url '%s'.", url));
    }
    String host = matcher.group("host");
    String port = matcher.group("port");
    String prefix = matcher.group("prefix");
    if (host == null) {
      throw new NullPointerException("Host is invalid. Use format: host:port");
    }
    if (port == null) {
      throw new NullPointerException("Port is invalid. Use format: host:port");
    }
    System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");

    JsonObject config = loadConfig();
    String dnsResolver1 = config.getString("dns_resolver_1", "1.1.1.1");
    String dnsResolver2 = config.getString("dns_resolver_2", "8.8.8.8");
    AddressResolverOptions resolverOpts = new AddressResolverOptions().addServer(dnsResolver1).addServer(dnsResolver2);
    VertxOptions opts = new VertxOptions().setAddressResolverOptions(resolverOpts);

    Vertx.vertx(opts).deployVerticle(new RestVerticle(host, port, prefix), ar -> {
      if (ar.failed()) {
        log.error("Failed to start.", ar.cause());
        return;
      }
      log.info("Rest mock started.");
    });
  }


  @Override
  public void start(Future<Void> future) throws IOException {
    log.info("Watching .json files in folder {}", DIR.toAbsolutePath());
    log.info("Use following json structure: \n{}", getJsonStructure());

    getInitialFiles(DIR, path -> addMock(path, false));

    try {
      DIR.register(watchService,
                   StandardWatchEventKinds.ENTRY_CREATE,
                   StandardWatchEventKinds.ENTRY_MODIFY,
                   StandardWatchEventKinds.ENTRY_DELETE);
    } catch (IOException e) {
      future.fail(e);
      return;
    }

    watchFiles();

    server = vertx.createHttpServer().requestHandler(router::accept).listen(port, host, serverAr -> {
      if (serverAr.failed()) {
        log.error("Failed to start http server.");
        future.fail(serverAr.cause());
        return;
      }
      log.info("Started http server on {}:{}{}", host, port, prefix);
      future.complete();
    });
  }

  private void watchFiles() {
    Map<WatchEvent.Kind, Consumer<String>> actions = new HashMap<>();
    actions.put(StandardWatchEventKinds.ENTRY_CREATE, fileName -> addMock(fileName, false));
    actions.put(StandardWatchEventKinds.ENTRY_MODIFY, fileName -> {
      log.info("Reloading mock from file {}.", fileName);
      removeMock(fileName, true);
      addMock(fileName, true);
    });
    actions.put(StandardWatchEventKinds.ENTRY_DELETE, fileName -> removeMock(fileName, false));

    vertx.setPeriodic(POLL_TIME, t -> {
      WatchKey key = watchService.poll();
      if (key == null) {
        return;
      }
      key.pollEvents().stream()
         .filter(event -> event.context().toString().endsWith(".json"))
         .forEach(event -> actions.get(event.kind()).accept(event.context().toString()));
      key.reset();
    });
  }

  @Override
  public void stop(Future<Void> future) throws Exception {
    if (watchService != null) {
      watchService.close();
    }
    if (server != null) {
      server.close(future);
      return;
    }
    future.complete();
  }

  private void addMock(String fileName, boolean reload) {
    addMock(DIR.resolve(fileName).toAbsolutePath(), reload);
  }

  private void addMock(Path absolutePath, boolean reload) {
    JsonObject json = bufferToJsonObject(vertx.fileSystem().readFileBlocking(absolutePath.toString()));

    if (json == null || json.isEmpty()) {
      log.warn("File is invalid or empty, ignoring. File: {}", absolutePath.toFile().getName());
      return;
    }
    if (isInvalid(json, URL, String.class)) {
      log.warn("File is missing '{}', ignoring. File: {}", URL, absolutePath.toFile().getName());
      return;
    }
    if (isInvalid(json, HTTP_CODE, Integer.class)) {
      log.warn("File is missing '{}', using '200'. File: {}", HTTP_CODE, absolutePath.toFile().getName());
    }
    if (isInvalid(json, HTTP_METHOD, String.class)) {
      log.warn("File is missing '{}', using 'GET'. File: {}", HTTP_METHOD, absolutePath.toFile().getName());
    }
    if (isInvalid(json, RESPONSE, JsonObject.class) && isInvalid(json, RESPONSE, JsonArray.class)) {
      log.warn("File is missing '{}', using '#empty#'. File: {}", RESPONSE, absolutePath.toFile().getName());
    }

    Mock mock = new Mock(json.getString(URL),
                         json.getInteger(HTTP_CODE, 200),
                         json.getString(HTTP_METHOD, "GET"),
                         json.getValue(RESPONSE) instanceof JsonObject
                             ? json.getJsonObject(RESPONSE, new JsonObject())
                             : null,
                         json.getValue(RESPONSE) instanceof JsonArray
                             ? json.getJsonArray(RESPONSE, new JsonArray())
                             : null,
                         absolutePath);
    Route route = createRoute(mock);
    if (route != null) {
      mocksByFileName.put(absolutePath.toFile().getName(), mock);
      routesByUrl.put(mock.getUrl(), route);
      if (!reload) {
        log.info("Loading mock from file: {}, url: {}", absolutePath.toFile().getName(), mock.getUrl());
      }
    }
  }

  private void removeMock(String fileName, boolean reload) {
    Mock mock = mocksByFileName.remove(fileName);
    if (mock == null) {
      return;
    }
    routesByUrl.get(mock.getUrl()).remove();
    if (!reload) {
      log.info("Removing deleted mock from file: {}, url: {}", fileName, mock.getUrl());
    }
  }

  private Route createRoute(Mock mock) {
    HttpMethod method;
    try {
      method = HttpMethod.valueOf(mock.getHttpMethod().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.error("Mock with url {} contains illegal http_method.", mock.getUrl());
      return null;
    }
    return router.routeWithRegex(method, mock.getPrefixUrl(prefix) + ".*").useNormalisedPath(true).handler(ctx -> {
      log.info(">>>>>>>>>>");
      log.info("Received request for url: {}", mock.getFullUrl(host, port, prefix));
      log.info("Using file: {}", mock.getAbsolutePath());
      ctx.request().headers().forEach(e -> log.info("Request header: {}: {}", e.getKey(), e.getValue()));
      ctx.request().params().forEach(e -> log.info("Request param: {}: {}", e.getKey(), e.getValue()));
      ctx.request().formAttributes().forEach(e -> log.info("Request form attribute: {}: {}", e.getKey(), e.getValue()));
      JsonObject json = bufferToJsonObject(ctx.getBody());
      String body = json != null ? "\n" + json.encodePrettily() : ctx.getBodyAsString().trim();
      log.info("Request body: {}", body.isEmpty() ? "#empty#" : body);

      log.info("<<<<<<<<<<");
      ctx.response()
         .setStatusCode(mock.getHttpCode())
         .putHeader("Content-Type", "application/json");
      log.info("Response http code: {}", ctx.response().getStatusCode());
      ctx.response().headers().forEach(e -> log.info("Response header: {}: {}", e.getKey(), e.getValue()));
      if (mock.getResponseJson() != null) {
        log.info("Response json: \n{}", mock.getResponseJson().encodePrettily());
        ctx.response().end(mock.getResponseJson().encodePrettily());
      } else {
        log.info("Response json array: \n{}", mock.getResponseArray().encodePrettily());
        ctx.response().end(mock.getResponseArray().encodePrettily());
      }
    });
  }

  private void addLastRoute() {
    JsonObject json = new JsonObject().put("404", "Response not found.");
    router.route().last().handler(ctx -> {
      log.warn("Received request on unknown url {}", ctx.request().uri());
      ctx.response()
         .setStatusCode(404)
         .putHeader("Content-Type", "application/json")
         .end(json.encodePrettily());
    });
  }
}
