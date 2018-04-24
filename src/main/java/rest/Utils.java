package rest;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.BufferedReader;
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
public class Utils {

  public static JsonObject loadJsonObject(String fileName) {
    try {
      return new JsonObject(readToString(Utils.class.getResourceAsStream(fileName)));
    } catch (IOException e) {
      log.error("Failed to load config file: {}", fileName);
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

  public static String getJsonStructure() {
    return new JsonObject()
        .put("url", "/example/url/.*/using/regex")
        .put("http_code", 200)
        .put("http_method", "GET")
        .put("response", new JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("key3", new JsonArray().add("value3").add("value4")))
        .encodePrettily();
  }

  public static JsonObject bufferToJsonObject(Buffer buffer) {
    try {
      return buffer.toJsonObject();
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean isJsonObject(String json) {
    try {
      new JsonObject(json);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isInvalid(JsonObject json, String key, Class type) {
    Object obj = json.getValue(key);
    return obj == null || !type.isInstance(obj) && !isJsonObject(obj.toString());
  }

  public static void getInitialFiles(Path dir, Consumer<Path> consumer) throws IOException {
    Files.list(dir)
         .filter(path -> !path.toFile().isDirectory())
         .filter(path -> path.toString().endsWith(".json"))
         .map(Path::toAbsolutePath)
         .forEach(consumer);
  }
}
