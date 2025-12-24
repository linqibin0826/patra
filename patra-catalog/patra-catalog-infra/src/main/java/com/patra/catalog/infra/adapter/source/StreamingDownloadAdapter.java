package com.patra.catalog.infra.adapter.source;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.error.trait.StandardErrorTrait;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/// 流式下载端口适配器。
///
/// 使用 WebClient + Reactor Netty 实现流式下载，解决 RestClient.exchange()
/// 在长时间流式传输过程中意外关闭 InputStream 的问题。
///
/// **超时配置**：使用 `streamingWebClient`（默认 10 分钟响应超时），
/// 适合大文件流式读取场景，可通过 `patra.rest-client.clients.long-running.timeout` 配置调整。
///
/// ## 实现原理
///
/// WebClient 返回 `Flux<DataBuffer>` 响应式流，通过 `PipedInputStream/PipedOutputStream`
/// 桥接转换为传统阻塞式 `InputStream`，供调用方同步读取。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class StreamingDownloadAdapter implements StreamingDownloadPort {

  private final WebClient webClient;

  /// 构造流式下载适配器。
  ///
  /// @param webClient WebClient 实例（使用 streamingWebClient，10 分钟响应超时）
  public StreamingDownloadAdapter(@Qualifier("streamingWebClient") WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public StreamingDownloadResult download(URI url) {
    Objects.requireNonNull(url, "下载 URL 不能为 null");
    log.info("开始流式下载：{}", url);

    try {
      // 获取响应头和 Flux<DataBuffer>
      var responseEntity =
          webClient
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
                                return new FileDownloadException(
                                    "HTTP 错误：" + response.statusCode().value(),
                                    StandardErrorTrait.DEP_UNAVAILABLE);
                              }))
              .toEntityFlux(DataBuffer.class)
              .block();

      if (responseEntity == null) {
        throw new FileDownloadException("响应为空", StandardErrorTrait.DEP_UNAVAILABLE);
      }

      HttpHeaders headers = responseEntity.getHeaders();
      long contentLength = headers.getContentLength();
      MediaType contentType = headers.getContentType();
      Flux<DataBuffer> bodyFlux = responseEntity.getBody();

      if (bodyFlux == null) {
        throw new FileDownloadException("响应体为空", StandardErrorTrait.DEP_UNAVAILABLE);
      }

      log.debug("流式下载连接建立成功：{}，Content-Length：{}，Content-Type：{}", url, contentLength, contentType);

      // 将 Flux<DataBuffer> 转换为阻塞式 InputStream
      InputStream inputStream = fluxToInputStream(bodyFlux);

      return new StreamingDownloadResult(
          inputStream, contentLength, contentType != null ? contentType.toString() : null);

    } catch (WebClientResponseException e) {
      log.error("流式下载 HTTP 错误：{}，状态码：{}", url, e.getStatusCode(), e);
      throw new FileDownloadException(
          "下载失败，HTTP 状态码：" + e.getStatusCode().value(), e, StandardErrorTrait.DEP_UNAVAILABLE);

    } catch (WebClientRequestException e) {
      log.error("流式下载网络错误：{}", url, e);
      StandardErrorTrait trait =
          e.getMessage() != null && e.getMessage().contains("timeout")
              ? StandardErrorTrait.TIMEOUT
              : StandardErrorTrait.DEP_UNAVAILABLE;
      throw new FileDownloadException("网络访问失败：" + e.getMessage(), e, trait);

    } catch (FileDownloadException e) {
      throw e;

    } catch (Exception e) {
      log.error("流式下载未知错误：{}", url, e);
      throw new FileDownloadException("下载失败：" + e.getMessage(), e);
    }
  }

  /// 将 `Flux<DataBuffer>` 转换为阻塞式 `InputStream`。
  ///
  /// 使用 `PipedInputStream/PipedOutputStream` 桥接 Reactive 流和传统 IO：
  /// - 使用 `DataBufferUtils.write()` 将 Flux 写入 OutputStream（自动释放 DataBuffer）
  /// - 在后台线程（`Schedulers.boundedElastic()`）执行写入操作
  /// - 调用方通过 PipedInputStream 同步读取数据
  ///
  /// **注意**：此方法是阻塞的，不适合在响应式环境（如 WebFlux）中使用。
  ///
  /// @param flux DataBuffer 响应式流
  /// @return 可阻塞读取的 InputStream
  private InputStream fluxToInputStream(Flux<DataBuffer> flux) {
    try {
      PipedInputStream inputStream = new PipedInputStream(65536); // 64KB 缓冲区
      PipedOutputStream outputStream = new PipedOutputStream(inputStream);

      // 使用 DataBufferUtils.write() 简化实现，自动处理 DataBuffer 释放
      DataBufferUtils.write(flux, outputStream)
          .subscribeOn(Schedulers.boundedElastic())
          .doFinally(signal -> closeQuietly(outputStream))
          .subscribe(
              DataBufferUtils.releaseConsumer(),
              error -> log.error("流式下载数据传输错误", error),
              () -> log.debug("流式下载数据传输完成"));

      return inputStream;

    } catch (IOException e) {
      throw new UncheckedIOException("创建管道流失败", e);
    }
  }

  /// 静默关闭输出流。
  ///
  /// @param outputStream 要关闭的输出流
  private void closeQuietly(OutputStream outputStream) {
    try {
      outputStream.close();
    } catch (IOException e) {
      log.warn("关闭输出流失败", e);
    }
  }
}
