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
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

/// 流式下载端口适配器。
///
/// 支持 HTTP/HTTPS 和 FTP 协议的流式下载，根据 URL scheme 自动选择下载方式。
///
/// ## HTTP/HTTPS 下载
///
/// 使用 WebClient + Reactor Netty 实现流式下载，解决 RestClient.exchange()
/// 在长时间流式传输过程中意外关闭 InputStream 的问题。
///
/// **超时配置**：使用 `streamingWebClient`（默认 30 分钟响应超时），
/// 适合大文件流式读取场景，可通过 `patra.rest-client.clients.long-running.timeout` 配置调整。
///
/// ## FTP 下载
///
/// 使用 Apache Commons Net FTPClient 实现，用于下载 NLM LSIOU 数据。
/// 采用被动模式（Passive Mode）和二进制传输，适合防火墙环境。
///
/// ## 重试机制
///
/// 对于瞬时网络错误（如 `PrematureCloseException`、连接超时等），
/// 自动进行指数退避重试（最多 3 次，初始延迟 2 秒）。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class StreamingDownloadAdapter implements StreamingDownloadPort {

  /// 最大重试次数。
  private static final int MAX_RETRIES = 3;

  /// 初始重试延迟。
  private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);

  /// 最大重试延迟。
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

  /// FTP 连接超时（毫秒）。
  private static final int FTP_CONNECT_TIMEOUT = 30_000;

  /// FTP 数据传输超时（毫秒）。
  private static final int FTP_DATA_TIMEOUT = 30 * 60_000;

  private final WebClient webClient;

  /// 构造流式下载适配器。
  ///
  /// @param webClient WebClient 实例（使用 streamingWebClient，30 分钟响应超时）
  public StreamingDownloadAdapter(@Qualifier("streamingWebClient") WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public StreamingDownloadResult download(URI url) {
    Objects.requireNonNull(url, "下载 URL 不能为 null");

    String scheme = url.getScheme();
    if (scheme == null) {
      throw new FileDownloadException("URL 缺少协议：" + url, StandardErrorTrait.RULE_VIOLATION);
    }

    return switch (scheme.toLowerCase()) {
      case "ftp" -> downloadViaFtp(url);
      case "http", "https" -> downloadViaHttp(url);
      default ->
          throw new FileDownloadException(
              "不支持的协议：" + scheme + "，仅支持 http/https/ftp", StandardErrorTrait.RULE_VIOLATION);
    };
  }

  // ========== FTP 下载 ==========

  /// 通过 FTP 协议下载文件。
  ///
  /// 使用 Apache Commons Net FTPClient，采用：
  /// - 被动模式（Passive Mode）：适合 NAT/防火墙环境
  /// - 二进制传输模式：确保数据完整性
  /// - 匿名登录：NLM FTP 服务器要求
  ///
  /// @param url FTP URL
  /// @return 流式下载结果
  private StreamingDownloadResult downloadViaFtp(URI url) {
    log.info("开始 FTP 下载：{}", url);

    FTPClient ftpClient = new FTPClient();
    ftpClient.setConnectTimeout(FTP_CONNECT_TIMEOUT);
    ftpClient.setDataTimeout(Duration.ofMillis(FTP_DATA_TIMEOUT));

    try {
      // 连接 FTP 服务器
      String host = url.getHost();
      int port = url.getPort() > 0 ? url.getPort() : 21;
      ftpClient.connect(host, port);

      int replyCode = ftpClient.getReplyCode();
      if (!FTPReply.isPositiveCompletion(replyCode)) {
        throw new FileDownloadException(
            "FTP 连接被拒绝，响应码：" + replyCode, StandardErrorTrait.DEP_UNAVAILABLE);
      }

      // 匿名登录（NLM FTP 服务器要求）
      if (!ftpClient.login("anonymous", "patra@example.com")) {
        throw new FileDownloadException("FTP 登录失败", StandardErrorTrait.DEP_UNAVAILABLE);
      }

      // 设置被动模式和二进制传输
      ftpClient.enterLocalPassiveMode();
      ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

      // 获取文件路径
      String remotePath = url.getPath();
      log.debug("FTP 连接成功：{}，下载路径：{}", host, remotePath);

      // 获取文件大小（可能返回 -1 表示未知）
      var ftpFile = ftpClient.mlistFile(remotePath);
      long fileSize = ftpFile != null ? ftpFile.getSize() : -1;

      // 获取输入流
      InputStream ftpInputStream = ftpClient.retrieveFileStream(remotePath);
      if (ftpInputStream == null) {
        throw new FileDownloadException(
            "无法获取 FTP 文件流：" + remotePath + "，响应：" + ftpClient.getReplyString(),
            StandardErrorTrait.DEP_UNAVAILABLE);
      }

      log.debug("FTP 文件流获取成功，文件大小：{}", fileSize > 0 ? fileSize + " bytes" : "未知");

      // 包装 InputStream，确保关闭时同时关闭 FTP 连接
      InputStream wrappedStream = new FtpInputStreamWrapper(ftpInputStream, ftpClient);

      return new StreamingDownloadResult(wrappedStream, fileSize, "application/xml");

    } catch (FileDownloadException e) {
      disconnectFtpQuietly(ftpClient);
      throw e;
    } catch (IOException e) {
      disconnectFtpQuietly(ftpClient);
      log.error("FTP 下载 IO 错误：{}", url, e);
      throw new FileDownloadException(
          "FTP 下载失败：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    } catch (Exception e) {
      disconnectFtpQuietly(ftpClient);
      log.error("FTP 下载未知错误：{}", url, e);
      throw new FileDownloadException(
          "FTP 下载失败：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  /// FTP InputStream 包装器。
  ///
  /// 确保关闭 InputStream 时同时完成 FTP 传输并断开连接。
  private static class FtpInputStreamWrapper extends InputStream {
    private final InputStream delegate;
    private final FTPClient ftpClient;

    FtpInputStreamWrapper(InputStream delegate, FTPClient ftpClient) {
      this.delegate = delegate;
      this.ftpClient = ftpClient;
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
        delegate.close();
        // 完成 FTP 传输
        if (!ftpClient.completePendingCommand()) {
          log.warn("FTP completePendingCommand 失败");
        }
      } finally {
        // 断开 FTP 连接
        if (ftpClient.isConnected()) {
          try {
            ftpClient.logout();
            ftpClient.disconnect();
          } catch (IOException e) {
            log.warn("断开 FTP 连接失败", e);
          }
        }
      }
    }
  }

  /// 静默断开 FTP 连接。
  private void disconnectFtpQuietly(FTPClient ftpClient) {
    if (ftpClient.isConnected()) {
      try {
        ftpClient.logout();
        ftpClient.disconnect();
      } catch (IOException e) {
        log.warn("断开 FTP 连接失败", e);
      }
    }
  }

  // ========== HTTP/HTTPS 下载 ==========

  /// 通过 HTTP/HTTPS 协议下载文件。
  ///
  /// @param url HTTP/HTTPS URL
  /// @return 流式下载结果
  private StreamingDownloadResult downloadViaHttp(URI url) {
    log.info("开始 HTTP 下载：{}", url);

    AtomicInteger attemptCount = new AtomicInteger(0);

    try {
      // 获取响应头和 Flux<DataBuffer>，带重试机制
      var responseEntity =
          createDownloadMono(url)
              .retryWhen(
                  Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                      .maxBackoff(MAX_BACKOFF)
                      .filter(this::isRetryableError)
                      .doBeforeRetry(
                          signal -> {
                            int attempt = attemptCount.incrementAndGet();
                            log.warn(
                                "HTTP 下载失败，准备第 {} 次重试：{}，错误：{}",
                                attempt,
                                url,
                                signal.failure().getMessage());
                          }))
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

      log.debug(
          "HTTP 下载连接建立成功：{}，Content-Length：{}，Content-Type：{}", url, contentLength, contentType);

      // 将 Flux<DataBuffer> 转换为阻塞式 InputStream
      InputStream inputStream = fluxToInputStream(bodyFlux, url);

      return new StreamingDownloadResult(
          inputStream, contentLength, contentType != null ? contentType.toString() : null);

    } catch (WebClientResponseException e) {
      log.error("HTTP 下载错误：{}，状态码：{}", url, e.getStatusCode(), e);
      throw new FileDownloadException(
          "下载失败，HTTP 状态码：" + e.getStatusCode().value(), e, StandardErrorTrait.DEP_UNAVAILABLE);

    } catch (WebClientRequestException e) {
      log.error("HTTP 下载网络错误：{}", url, e);
      StandardErrorTrait trait =
          e.getMessage() != null && e.getMessage().contains("timeout")
              ? StandardErrorTrait.TIMEOUT
              : StandardErrorTrait.DEP_UNAVAILABLE;
      throw new FileDownloadException("网络访问失败：" + e.getMessage(), e, trait);

    } catch (FileDownloadException e) {
      throw e;

    } catch (Exception e) {
      log.error("HTTP 下载未知错误：{}", url, e);
      throw new FileDownloadException(
          "下载失败：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  /// 创建下载 Mono。
  ///
  /// 将 WebClient 调用封装为 Mono，便于添加重试机制。
  ///
  /// @param url 下载 URL
  /// @return 包含响应实体的 Mono
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
                          return new FileDownloadException(
                              "HTTP 错误：" + response.statusCode().value(),
                              StandardErrorTrait.DEP_UNAVAILABLE);
                        }))
        .toEntityFlux(DataBuffer.class);
  }

  /// 判断错误是否可重试。
  ///
  /// 可重试的错误包括：
  /// - `PrematureCloseException`：连接在响应过程中被过早关闭
  /// - `WebClientRequestException`：网络请求异常（连接超时、DNS 解析失败等）
  /// - `IOException`：底层 IO 异常
  ///
  /// 不可重试的错误：
  /// - HTTP 4xx/5xx 响应（由 `onStatus` 处理，转换为 `FileDownloadException`）
  /// - 业务异常（`FileDownloadException`）
  ///
  /// @param throwable 异常
  /// @return 是否可重试
  private boolean isRetryableError(Throwable throwable) {
    // PrematureCloseException：连接过早关闭
    if (throwable instanceof PrematureCloseException) {
      return true;
    }

    // WebClientRequestException：网络请求异常
    if (throwable instanceof WebClientRequestException) {
      return true;
    }

    // IOException：底层 IO 异常
    if (throwable instanceof IOException) {
      return true;
    }

    // 检查 cause 链
    Throwable cause = throwable.getCause();
    if (cause != null && cause != throwable) {
      return isRetryableError(cause);
    }

    return false;
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
  /// @param url 下载 URL（用于日志）
  /// @return 可阻塞读取的 InputStream
  private InputStream fluxToInputStream(Flux<DataBuffer> flux, URI url) {
    try {
      PipedInputStream inputStream = new PipedInputStream(65536); // 64KB 缓冲区
      PipedOutputStream outputStream = new PipedOutputStream(inputStream);

      // 使用 DataBufferUtils.write() 简化实现，自动处理 DataBuffer 释放
      DataBufferUtils.write(flux, outputStream)
          .subscribeOn(Schedulers.boundedElastic())
          .doFinally(signal -> closeQuietly(outputStream))
          .subscribe(
              DataBufferUtils.releaseConsumer(),
              error -> log.error("HTTP 下载数据传输错误：{}", url, error),
              () -> log.debug("HTTP 下载数据传输完成：{}", url));

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
