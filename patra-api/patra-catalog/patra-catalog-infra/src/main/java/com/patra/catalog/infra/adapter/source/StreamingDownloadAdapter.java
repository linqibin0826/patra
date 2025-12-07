package com.patra.catalog.infra.adapter.source;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.error.trait.StandardErrorTrait;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/// 流式下载端口适配器。
///
/// 使用 RestClient 直接返回 HTTP 响应体流，无磁盘落盘。
///
/// **超时配置**：使用 `longRunningRestClient`（默认 10 分钟读取超时），
/// 适合大文件流式读取场景，可通过 `patra.rest-client.clients.long-running.timeout` 配置调整。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class StreamingDownloadAdapter implements StreamingDownloadPort {

  private final RestClient restClient;

  /// 构造流式下载适配器。
  ///
  /// @param restClient RestClient 实例（使用 longRunningRestClient，10 分钟读取超时）
  public StreamingDownloadAdapter(@Qualifier("longRunningRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public StreamingDownloadResult download(URI url) {
    Objects.requireNonNull(url, "下载 URL 不能为 null");
    log.info("开始流式下载：{}", url);

    try {
      // 使用 exchange 获取完整响应，手动管理 InputStream 生命周期
      // close=false 表示不自动关闭响应体，由调用方负责关闭
      return restClient
          .get()
          .uri(url)
          .exchange(
              (request, response) -> {
                HttpStatusCode status = response.getStatusCode();
                if (status.isError()) {
                  throw new FileDownloadException(
                      "HTTP 错误：" + status.value(), StandardErrorTrait.DEP_UNAVAILABLE);
                }

                HttpHeaders headers = response.getHeaders();
                long contentLength = headers.getContentLength();
                MediaType contentType = headers.getContentType();

                InputStream inputStream = response.getBody();

                log.debug(
                    "流式下载连接建立成功：{}，Content-Length：{}，Content-Type：{}",
                    url,
                    contentLength,
                    contentType);

                return new StreamingDownloadResult(
                    inputStream,
                    contentLength,
                    contentType != null ? contentType.toString() : null);
              },
              false);

    } catch (RestClientResponseException e) {
      log.error("流式下载 HTTP 错误：{}，状态码：{}", url, e.getStatusCode(), e);
      throw new FileDownloadException(
          "下载失败，HTTP 状态码：" + e.getStatusCode().value(), e, StandardErrorTrait.DEP_UNAVAILABLE);

    } catch (ResourceAccessException e) {
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
}
