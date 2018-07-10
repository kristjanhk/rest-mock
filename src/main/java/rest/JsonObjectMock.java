package rest;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Kristjan Hendrik Küngas
 */
@Slf4j
public class JsonObjectMock extends AbstractMock {
  private final JsonObject responseJson;

  JsonObjectMock(String url, int httpCode, String httpMethod, JsonObject responseJson, Path absolutePath, Map<String, Object> headers) {
    super(url, httpCode, httpMethod, absolutePath, headers);
    this.responseJson = responseJson;
  }

  @Override
  public void complete(HttpServerResponse res) {
    res.putHeader("Content-Type", "application/json");
    super.complete(res);
    res.headers().forEach(e -> log.info("Response header: {}: {}", e.getKey(), e.getValue()));
    log.info("Response json: \n{}", responseJson.encodePrettily());
    res.end(responseJson.encodePrettily());
  }
}
