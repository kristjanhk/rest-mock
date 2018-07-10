package rest;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author <a href="https://github.com/kristjanhk">Kristjan Hendrik Küngas</a>
 */
@Data
@AllArgsConstructor
public abstract class AbstractMock {
  private String url;
  private int httpCode;
  private String httpMethod;
  private Path absolutePath;
  private Map<String, Object> headers;

  public String getUrl() {
    return normalizeUrl(url);
  }

  public String getFullUrl(String host, int port, String prefix) {
    return String.format("http://%s:%s%s%s", host, port, normalizeUrl(prefix), normalizeUrl(url));
  }

  public String getPrefixUrl(String prefix) {
    return normalizeUrl(prefix) + normalizeUrl(url);
  }

  private String normalizeUrl(String urlRegex) {
    if (urlRegex == null || urlRegex.isEmpty()) {
      return "";
    }
    urlRegex = urlRegex.charAt(0) != '/' ? "/" + urlRegex : urlRegex;
    urlRegex = urlRegex.charAt(urlRegex.length() - 1) == '/' ? urlRegex.substring(0, urlRegex.length() - 1) : urlRegex;
    return urlRegex;
  }

  public void complete(HttpServerResponse res) {
    for (Map.Entry<String, Object> header : headers.entrySet())
    {
      res.putHeader(header.getKey(),  (String) header.getValue());
    }
  }

  public static AbstractMock create(String url, int httpCode, String httpMethod, Object response, Path absolutePath,Map<String, Object> headers) {
    if (response instanceof JsonObject) {
      return new JsonObjectMock(url, httpCode, httpMethod, (JsonObject) response, absolutePath, headers);
    }
    if (response instanceof JsonArray) {
      return new JsonArrayMock(url, httpCode, httpMethod, (JsonArray) response, absolutePath, headers);
    }
    if (response instanceof String) {
      return new FileMock(url, httpCode, httpMethod, (String) response, absolutePath, headers);
    }
    return new EmptyMock(url, httpCode, httpMethod, absolutePath);
  }
}
