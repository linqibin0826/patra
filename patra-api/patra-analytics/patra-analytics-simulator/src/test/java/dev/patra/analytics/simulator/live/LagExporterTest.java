package dev.patra.analytics.simulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LagExporterTest {

  private static final String RESPONSE =
      """
      {"status":"success","data":{"resultType":"matrix","result":[
        {"metric":{"topic":"events.raw"},"values":[[1762048800,"120"],[1762048815,"80"]]},
        {"metric":{"topic":"events.dlq"},"values":[[1762048800,"0"]]}]}}""";

  @Test
  void exports_one_row_per_point() throws Exception {
    AtomicReference<String> query = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/query_range",
        exchange -> {
          query.set(
              URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
          byte[] body = RESPONSE.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
      StringWriter csv = new StringWriter();

      new LagExporter(base, LagExporter.DEFAULT_QUERY)
          .export(Instant.ofEpochSecond(1762048800), Instant.ofEpochSecond(1762050600), csv);

      assertThat(query.get())
          .contains("query=" + LagExporter.DEFAULT_QUERY)
          .contains("start=1762048800")
          .contains("end=1762050600")
          .contains("step=15");
      assertThat(csv.toString().split("\n"))
          .containsExactly(
              "time,series,value",
              "2025-11-02T02:00:00Z,events.raw,120",
              "2025-11-02T02:00:15Z,events.raw,80",
              "2025-11-02T02:00:00Z,events.dlq,0");
    } finally {
      server.stop(0);
    }
  }
}
