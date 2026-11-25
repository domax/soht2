/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/** Verifies the default exposure of actuator endpoints, error details, and security headers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ServerExposureTest {

  static final HttpClient HTTP = HttpClient.newHttpClient();
  static final JsonMapper JSON = JsonMapper.builder().build();

  @LocalServerPort int port;

  @SneakyThrows
  HttpResponse<String> send(HttpRequest.Builder builder) {
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpRequest.Builder get(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
  }

  @SuppressWarnings("SameParameterValue")
  static String basic(String username, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void health_anonymous_statusOnly() {
    val response = send(get("/actuator/health"));
    assertThat(response.statusCode()).isEqualTo(200);
    val body = JSON.readTree(response.body());
    // only the overall status (and probe group names); no component details
    assertThat(body.propertyNames()).contains("status").isSubsetOf("status", "groups");
    assertThat(body.get("status").asString()).isEqualTo("UP");
  }

  @ParameterizedTest
  @CsvSource({
    "/actuator/heapdump,",
    "/actuator/env,",
    "/actuator/heapdump,admin",
    "/actuator/env,admin"
  })
  void otherActuatorEndpoints_notServed(String path, String user) {
    val request = get(path);
    if (user != null) request.header("Authorization", basic(user, "test"));
    assertThat(send(request).statusCode()).isNotEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"?trace=true", "?trace=true&exception=true"})
  void errorResponse_hidesInternalDetails(String query) {
    val response =
        send(
            get("/api/connection/history" + query + "&sort=invalid")
                .header("Authorization", basic("admin", "test")));
    assertThat(response.statusCode()).isGreaterThanOrEqualTo(400);
    val body = JSON.readTree(response.body());
    assertThat(body.has("trace")).isFalse();
    assertThat(body.has("exception")).isFalse();
    assertThat(body.has("status")).isTrue();
    assertThat(body.has("message")).isTrue();
  }

  @Test
  void indexPage_cannotBeFramed() {
    val response = send(get("/"));
    assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
  }

  @Test
  void cors_default_noAllowOrigin() {
    val response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/connection"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"));
    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }
}
