package com.patra.starter.restclient.download.strategy;

import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.starter.restclient.config.DownloadProperties;
import com.patra.starter.restclient.download.DownloadException;
import com.patra.starter.restclient.download.DownloadOptions;
import com.patra.starter.restclient.download.StreamingDownloadResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

/// HTTP/HTTPS 流式下载策略。
///
/// 使用 WebClient + Reactor Netty 提供稳定的流式下载能力。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class HttpStreamingDownloader implements StreamingDownloader {

  private final WebClient webClient;
  private final DownloadProperties properties;

  /// 创建 HTTP/HTTPS 流式下载策略。
  ///
  /// @param webClient WebClient 实例（建议使用 streamingWebClient）
  /// @param properties 下载配置
  public HttpStreamingDownloader(WebClient webClient, DownloadProperties properties) {
    this.webClient = webClient;
    this.properties = properties;
  }

  @Override
  public boolean supports(URI url) {
    if (url == null || url.getScheme() == null) {
      return false;
    }
    String scheme = url.getScheme().toLowerCase();
    return "http".equals(scheme) || "https".equals(scheme);
  }

  @Override
  public StreamingDownloadResponse openStream(URI url, DownloadOptions options) {
    Objects.requireNonNull(url, "下载 URL 不能为 null");
    log.info("开始 HTTP 下载：{}", url);

    var retryConfig = properties.getRetry();
    AtomicInteger attemptCount = new AtomicInteger(0);

    try {
      Mono<org.springframework.http.ResponseEntity<Flux<DataBuffer>>> mono =
          createDownloadMono(url);

      if (retryConfig.isEnabled()) {
        mono =
            mono.retryWhen(
                Retry.backoff(retryConfig.getMaxAttempts(), retryConfig.getInitialBackoff())
                    .maxBackoff(retryConfig.getMaxBackoff())
                    .filter(this::isRetryableError)
                    .doBeforeRetry(
                        signal -> {
                          int attempt = attemptCount.incrementAndGet();
                          log.warn(
                              "HTTP 下载失败，准备第 {} 次重试：{}，错误：{}",
                              attempt,
                              url,
                              signal.failure().getMessage());
                        }));
      }

      var responseEntity = mono.block();
      if (responseEntity == null) {
        throw new DownloadException("响应为空", StandardErrorTrait.DEP_UNAVAILABLE);
      }

      HttpHeaders headers = responseEntity.getHeaders();
      long contentLength = headers.getContentLength();
      MediaType contentType = headers.getContentType();
      Flux<DataBuffer> bodyFlux = responseEntity.getBody();

      if (bodyFlux == null) {
        throw new DownloadException("响应体为空", StandardErrorTrait.DEP_UNAVAILABLE);
      }

      log.debug(
          "HTTP 下载连接建立成功：{}，Content-Length：{}，Content-Type：{}", url, contentLength, contentType);

      int bufferSize =
          options != null && options.bufferSize() != null
              ? options.bufferSize()
              : properties.getBufferSize();
      InputStream inputStream = fluxToInputStream(bodyFlux, url, bufferSize);

      return new StreamingDownloadResponse(
          inputStream, contentLength, contentType != null ? contentType.toString() : null);

    } catch (WebClientResponseException e) {
      log.error("HTTP 下载错误：{}，状态码：{}", url, e.getStatusCode(), e);
      throw DownloadException.httpError(e.getStatusCode().value());

    } catch (WebClientRequestException e) {
      log.error("HTTP 下载网络错误：{}", url, e);
      if (isTimeout(e)) {
        throw DownloadException.timeout(e);
      }
      throw new DownloadException(
          "网络访问失败：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);

    } catch (DownloadException e) {
      throw e;

    } catch (Exception e) {
      log.error("HTTP 下载未知错误：{}", url, e);
      throw new DownloadException("下载失败：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  private Mono<org.springframework.http.ResponseEntity<Flux<DataBuffer>>> createDownloadMono(
      URI url) {
    return webClient
        .get()
        .uri(url)
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            response ->
                response
                    .bodyToMono(String.class)
                    .map(
                        body -> {
                          log.error("HTTP 错误响应：{}，body: {}", response.statusCode(), body);
                          return new DownloadException(
                              "HTTP 错误：" + response.statusCode().value(),
                              StandardErrorTrait.DEP_UNAVAILABLE);
                        }))
        .toEntityFlux(DataBuffer.class);
  }

  private boolean isTimeout(WebClientRequestException e) {
    String message = e.getMessage();
    if (message != null && message.contains("timeout")) {
      return true;
    }
    Throwable cause = e.getCause();
    return cause != null && cause.getMessage() != null && cause.getMessage().contains("timeout");
  }

  private boolean isRetryableError(Throwable throwable) {
    if (throwable instanceof PrematureCloseException) {
      return true;
    }
    if (throwable instanceof WebClientRequestException) {
      return true;
    }
    if (throwable instanceof IOException) {
      return true;
    }
    Throwable cause = throwable.getCause();
    if (cause != null && cause != throwable) {
      return isRetryableError(cause);
    }
    return false;
  }

  private InputStream fluxToInputStream(Flux<DataBuffer> flux, URI url, int bufferSize) {
    try {
      PipedInputStream inputStream = new PipedInputStream(bufferSize);
      PipedOutputStream outputStream = new PipedOutputStream(inputStream);

      var disposable =
          DataBufferUtils.write(flux, outputStream)
              .subscribeOn(Schedulers.boundedElastic())
              .doFinally(signal -> closeQuietly(outputStream))
              .subscribe(
                  DataBufferUtils.releaseConsumer(),
                  error -> log.error("HTTP 下载数据传输错误：{}", url, error),
                  () -> log.debug("HTTP 下载数据传输完成：{}", url));

      return new DisposableInputStream(inputStream, disposable);

    } catch (IOException e) {
      throw new UncheckedIOException("创建管道流失败", e);
    }
  }

  private void closeQuietly(OutputStream outputStream) {
    try {
      outputStream.close();
    } catch (IOException e) {
      log.warn("关闭输出流失败", e);
    }
  }

  /// 可取消的输入流包装。
  ///
  /// 在关闭时主动取消订阅，避免后台线程泄漏。
  private static class DisposableInputStream extends InputStream {
    private final InputStream delegate;
    private final reactor.core.Disposable disposable;

    DisposableInputStream(InputStream delegate, reactor.core.Disposable disposable) {
      this.delegate = delegate;
      this.disposable = disposable;
    }

    @Override
    public int read() throws IOException {
      return delegate.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return delegate.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
      try {
        disposable.dispose();
      } finally {
        delegate.close();
      }
    }
  }
}
