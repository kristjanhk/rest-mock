package rest;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author <a href="https://github.com/kristjanhk">Kristjan Hendrik Küngas</a>
 */
@Data
@AllArgsConstructor
public class Mock {
  private String url;
  private int httpCode;
  private String httpMethod;
  private JsonObject responseJson;
  private JsonArray responseArray;
  private Path absolutePath;

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
}
