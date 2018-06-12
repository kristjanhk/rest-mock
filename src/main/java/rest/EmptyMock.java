package rest;

import io.vertx.core.http.HttpServerResponse;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Kristjan Hendrik Küngas
 */
@Slf4j
public class EmptyMock extends AbstractMock {

  EmptyMock(String url, int httpCode, String httpMethod, Path absolutePath) {
    super(url, httpCode, httpMethod, absolutePath);
  }

  @Override
  public void complete(HttpServerResponse res) {
    log.info("Response is empty, valid Json, JsonArray or file was not found");
    res.end();
  }
}
