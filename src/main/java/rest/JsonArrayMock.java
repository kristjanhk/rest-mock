package rest;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Kristjan Hendrik Küngas
 */
@Slf4j
public class JsonArrayMock extends AbstractMock {
  private final JsonArray responseArray;

  JsonArrayMock(String url, int httpCode, String httpMethod, JsonArray responseArray, Path absolutePath, Map<String, Object> headers) {
    super(url, httpCode, httpMethod, absolutePath, headers);
    this.responseArray = responseArray;
  }

  @Override
  public void complete(HttpServerResponse res) {
    res.putHeader("Content-Type", "application/json");
    super.complete(res);
    res.headers().forEach(e -> log.info("Response header: {}: {}", e.getKey(), e.getValue()));
    log.info("Response json array: \n{}", responseArray.encodePrettily());
    res.end(responseArray.encodePrettily());
  }
}
