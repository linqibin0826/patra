package dev.patra.analytics.ingest;

import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.testcontainers.clickhouse.ClickHouseContainer;

/// 本机端口到 ClickHouse 容器 HTTP 端口的转发；关掉时拒绝新连接并断开已有连接。
final class TcpRelay implements AutoCloseable {

  private final ServerSocket server;
  private final List<Socket> sockets = new CopyOnWriteArrayList<>();
  private volatile boolean enabled = true;

  private TcpRelay(ServerSocket server) {
    this.server = server;
  }

  static TcpRelay start() {
    try {
      ServerSocket server = new ServerSocket();
      server.bind(new InetSocketAddress("127.0.0.1", 0));
      TcpRelay relay = new TcpRelay(server);
      Thread.ofVirtual().start(relay::acceptLoop);
      return relay;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  int port() {
    return server.getLocalPort();
  }

  void disable() {
    enabled = false;
    sockets.forEach(TcpRelay::closeQuietly);
    sockets.clear();
  }

  void enable() {
    enabled = true;
  }

  @Override
  public void close() {
    disable();
    closeQuietly(server);
  }

  private void acceptLoop() {
    while (!server.isClosed()) {
      try {
        Socket client = server.accept();
        if (!enabled) {
          closeQuietly(client);
          continue;
        }
        ClickHouseContainer container = ClickHouseContainerInitializer.getClickHouseContainer();
        Socket upstream = new Socket(container.getHost(), container.getMappedPort(8123));
        sockets.add(client);
        sockets.add(upstream);
        Thread.ofVirtual().start(() -> pipe(client, upstream));
        Thread.ofVirtual().start(() -> pipe(upstream, client));
      } catch (IOException e) {
        // 转发关掉或连接断开
      }
    }
  }

  private static void pipe(Socket from, Socket to) {
    try (InputStream in = from.getInputStream();
        OutputStream out = to.getOutputStream()) {
      in.transferTo(out);
    } catch (IOException e) {
      // 一端断开
    } finally {
      closeQuietly(from);
      closeQuietly(to);
    }
  }

  private static void closeQuietly(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception e) {
      // 已关闭
    }
  }
}
