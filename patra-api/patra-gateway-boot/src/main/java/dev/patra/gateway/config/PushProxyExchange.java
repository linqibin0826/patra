package dev.patra.gateway.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.handler.RestClientProxyExchange;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/// 推送路由的代理交换（PAP-145）：每条 SSE 事件一到就转给浏览器；网关停机时结束正在转发的推送流。
///
/// **逐条事件刷出**：Gateway 对事件流的做法是「读一块、刷一次」，但 Reactor 客户端的响应体输入流
/// （Spring 7.0.10 的 `SubscriberInputStream`）一次读要读满缓冲区（16 KB）或读到流结束才返回，几十字节的
/// 事件和心跳会一直攒在网关里，推送等于没有。这里逐字节读（单字节读只等「有数据」），写进 Tomcat 的响应
/// 缓冲，读到空行（一条事件的结尾，notification 用 `\n` 换行）就刷出去。
///
/// **停机**：推送流挂 30 分钟才结束，Boot 的优雅停机会一直等它（最多 30 秒），容器先被强杀。停机开始时
/// 关掉所有正在转发的下游响应体，转发立即正常结束，浏览器 3 秒后重连。
@Slf4j
public class PushProxyExchange extends RestClientProxyExchange {

  /// 正在转发的下游响应体。
  private final Set<InputStream> openBodies = ConcurrentHashMap.newKeySet();

  /// 创建。
  ///
  /// @param restClient 用推送专用连接池的客户端
  /// @param properties Gateway 的配置
  public PushProxyExchange(RestClient restClient, GatewayMvcProperties properties) {
    super(restClient, properties);
  }

  /// 把下游的事件流转给浏览器，每条事件结束（空行）刷一次。
  ///
  /// @param clientResponse 下游响应
  /// @param inputStream 下游响应体
  /// @param outputStream 浏览器那边的响应体
  /// @return 转发的字节数
  /// @throws IOException 读写失败时
  @Override
  protected int copyResponseBody(
      ClientHttpResponse clientResponse, InputStream inputStream, OutputStream outputStream)
      throws IOException {
    openBodies.add(inputStream);
    try {
      int transferred = 0;
      int previous = -1;
      int current;
      while ((current = inputStream.read()) != -1) {
        outputStream.write(current);
        transferred++;
        if (current == '\n' && previous == '\n') {
          outputStream.flush();
        }
        previous = current;
      }
      outputStream.flush();
      return transferred;
    } finally {
      openBodies.remove(inputStream);
    }
  }

  /// 关掉所有正在转发的下游响应体：卡在读上的转发读到结尾、正常结束。停机开始时调用。
  public void closeOpenStreams() {
    for (InputStream body : openBodies) {
      try {
        body.close();
      } catch (IOException e) {
        log.debug("停机时关推送流失败，忽略", e);
      }
    }
  }
}
