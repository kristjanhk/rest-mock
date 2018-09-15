package rest;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Kristjan Hendrik Küngas
 */
@Slf4j
public class FileMock extends AbstractMock {
  private final String fileName;

  FileMock(String url, int httpCode, String httpMethod, String fileName, Path absolutePath, Map<String, Object> headers) {
    super(url, httpCode, httpMethod, absolutePath, headers);
    this.fileName = fileName;
  }

  @Override
  public void complete(HttpServerResponse res) {
    super.complete(res);
    String absolutePath = RestVerticle.DIR.resolve(fileName).toAbsolutePath().toString();
    Vertx.currentContext().owner().fileSystem().readFile(absolutePath, ar -> {
      if (ar.failed()) {
        log.error("{} file is missing, using empty response", fileName);
        res.end();
        return;
      }
      log.info("Response body: \n{}", ar.result().toString(StandardCharsets.UTF_8));
      res.headers().forEach(e -> log.info("Response header: {}: {}", e.getKey(), e.getValue()));
      res.end(ar.result());
    });
  }
}
