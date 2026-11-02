package dev.patra.analytics.simulator.live;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.json.JsonMapper;

/// 假采集端点：按预设依次返回状态码（用完后一直 202），记下收到的请求体与最大并发。
final class FakeCollector implements AutoCloseable {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  final List<String> bodies = new CopyOnWriteArrayList<>();
  final ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();
  final AtomicInteger concurrent = new AtomicInteger();
  final AtomicInteger maxConcurrent = new AtomicInteger();
  volatile long delayMillis;
  volatile int rejected;
  private final HttpServer server;

  FakeCollector() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.createContext(
        "/events",
        exchange -> {
          int now = concurrent.incrementAndGet();
          maxConcurrent.accumulateAndGet(now, Math::max);
          try {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            if (delayMillis > 0) {
              Thread.sleep(delayMillis);
            }
            Integer status = statuses.poll();
            int code = status != null ? status : 202;
            int events = MAPPER.readTree(body).get("events").size();
            byte[] response =
                (code == 202
                        ? "{\"accepted\":" + (events - rejected) + ",\"rejected\":" + rejected + "}"
                        : "{\"title\":\"x\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, response.length);
            exchange.getResponseBody().write(response);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            concurrent.decrementAndGet();
            exchange.close();
          }
        });
    server.start();
  }

  URI uri() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/events");
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
