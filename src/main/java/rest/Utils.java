package rest;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author <a href="https://github.com/kristjanhk">Kristjan Hendrik Küngas</a>
 */
@Slf4j
class Utils {

  static JsonObject loadConfig() {
    try {
      return new JsonObject(readToString(new FileInputStream("config/config.json")));
    } catch (IOException e) {
      log.error("Failed to load config file: {}. The config file should be structured like following: {}", "config/config.json",
              new JsonObject().put("dns_resolver_1", "192.168.41.1").put("dns_resolver_2", "8.8.8.8").encodePrettily());
    }
    return new JsonObject();
  }

  private static String readToString(InputStream inputStream) throws IOException {
    if (inputStream == null) {
      throw new FileNotFoundException();
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      return reader.lines().collect(Collectors.joining("\n"));
    }
  }

  static String getJsonStructure() {
    return new JsonObject()
        .put("headers", new JsonObject().put("header_key", "header_value").put("header_key2", "header_value2"))
        .put("url", "/example/url/.*/using/regex")
        .put("http_code", 200)
        .put("http_method", "GET")
        .put("response", "Valid Json, JsonArray or file name")
        .encodePrettily();
  }

  static JsonObject bufferToJsonObject(Buffer buffer) {
    try {
      return buffer.toJsonObject();
    } catch (Exception e) {
      return null;
    }
  }

  static boolean isInvalid(JsonObject json, String key, Class type) {
    Object obj = json.getValue(key);
    return obj == null || !type.isInstance(obj);
  }

  static void getInitialFiles(Path dir, Consumer<Path> consumer) throws IOException {
    Files.list(dir)
         .filter(path -> !path.toFile().isDirectory())
         .filter(path -> path.toString().endsWith(".json"))
         .map(Path::toAbsolutePath)
         .forEach(consumer);
  }
}
