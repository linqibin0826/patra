package com.patra.catalog.infra.adapter.source;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.starter.restclient.download.DownloadClient;
import com.patra.starter.restclient.download.DownloadException;
import com.patra.starter.restclient.download.DownloadOptions;
import com.patra.starter.restclient.download.StreamingDownloadResponse;
import java.net.URI;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 流式下载端口适配器。
///
/// 通过 Starter 的 DownloadClient 提供流式下载能力，
/// 仅负责协议转换与异常语义映射。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class StreamingDownloadAdapter implements StreamingDownloadPort {

  private final DownloadClient downloadClient;
  private static final String FTP_USERNAME = "anonymous";
  private static final String FTP_PASSWORD = "patra@example.com";

  /// 构造流式下载适配器。
  ///
  /// @param downloadClient 下载客户端
  public StreamingDownloadAdapter(DownloadClient downloadClient) {
    this.downloadClient = downloadClient;
  }

  @Override
  public StreamingDownloadResult download(URI url) {
    Objects.requireNonNull(url, "下载 URL 不能为 null");

    try {
      StreamingDownloadResponse result = downloadClient.openStream(url, resolveOptions(url));
      return new StreamingDownloadResult(
          result.inputStream(), result.contentLength(), result.contentType());
    } catch (DownloadException e) {
      throw new FileDownloadException(e.getMessage(), e, mapErrorTrait(e));
    } catch (Exception e) {
      log.error("流式下载失败：{}", url, e);
      throw new FileDownloadException(
          "下载失败：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  /// 构建下载选项（FTP 场景注入账号密码）。
  ///
  /// @param url 下载 URL
  /// @return 下载选项（非 FTP 返回 null）
  private DownloadOptions resolveOptions(URI url) {
    if (!isFtp(url)) {
      return null;
    }
    return DownloadOptions.withFtpCredentials(FTP_USERNAME, FTP_PASSWORD);
  }

  /// 判断是否为 FTP 协议。
  ///
  /// @param url 下载 URL
  /// @return 是否为 FTP
  private boolean isFtp(URI url) {
    return url != null && "ftp".equalsIgnoreCase(url.getScheme());
  }

  private StandardErrorTrait mapErrorTrait(DownloadException exception) {
    if (exception.getErrorTraits() == null || exception.getErrorTraits().isEmpty()) {
      return StandardErrorTrait.DEP_UNAVAILABLE;
    }
    return exception.getErrorTraits().stream()
        .filter(StandardErrorTrait.class::isInstance)
        .map(StandardErrorTrait.class::cast)
        .findFirst()
        .orElse(StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
