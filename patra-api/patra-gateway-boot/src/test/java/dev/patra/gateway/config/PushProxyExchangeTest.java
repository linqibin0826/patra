package dev.patra.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/// 推送路由的代理交换：网关停机时结束正在转发的推送流。
class PushProxyExchangeTest {

  private final PushProxyExchange exchange =
      new PushProxyExchange(RestClient.create(), new GatewayMvcProperties());

  /// 停机时关掉所有正在转发的下游响应体：卡在读上的转发立即结束，网关的优雅停机不用等推送连接。
  @Test
  void should_end_copy_when_open_streams_are_closed() throws Exception {
    AwaitingBody body = new AwaitingBody();
    CompletableFuture<Integer> copy =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return exchange.copyResponseBody(
                    mock(ClientHttpResponse.class), body, new ByteArrayOutputStream());
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    assertThat(body.reading.await(5, TimeUnit.SECONDS)).isTrue();

    exchange.closeOpenStreams();

    assertThat(copy.get(1, TimeUnit.SECONDS)).isZero();
  }

  /// 一直等数据的下游响应体：只有被关掉才读到结尾，开始读时报一声。
  private static final class AwaitingBody extends InputStream {

    private final CountDownLatch reading = new CountDownLatch(1);
    private final CountDownLatch closed = new CountDownLatch(1);

    /// 等到被关掉，返回流结束。
    ///
    /// @return 流结束（-1）
    /// @throws InterruptedIOException 等待时被打断
    @Override
    public int read() throws InterruptedIOException {
      reading.countDown();
      try {
        closed.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new InterruptedIOException();
      }
      return -1;
    }

    /// 关掉：放开等在读上的线程。
    @Override
    public void close() {
      closed.countDown();
    }
  }
}
