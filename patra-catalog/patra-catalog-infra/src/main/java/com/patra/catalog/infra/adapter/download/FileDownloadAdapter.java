package com.patra.catalog.infra.adapter.download;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.common.error.trait.StandardErrorTrait;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/// 文件下载端口适配器。
///
/// 使用 longRunningRestClient 下载远程文件到系统临时目录。
///
/// **技术选型**：使用 RestClient 而非 WebClient，因为：
///
/// - 同步下载更简单（300MB 文件一次性下载）
/// - 与项目现有技术栈一致
///
/// **超时配置**：使用 longRunningRestClient（默认 10 分钟读取超时），
/// 适合大文件下载场景，可通过 `patra.rest-client.clients.long-running.timeout` 配置调整。
///
/// **文件命名**：`mesh-import-{uuid}.xml`
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class FileDownloadAdapter implements FileDownloadPort {

  private static final String TEMP_FILE_PREFIX = "mesh-import-";
  private static final String TEMP_FILE_SUFFIX = ".xml";

  private final RestClient restClient;

  /// 构造文件下载适配器。
  ///
  /// @param restClient 长时间运行 RestClient（10 分钟读取超时）
  public FileDownloadAdapter(@Qualifier("longRunningRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public Path downloadToTemp(URI url) {
    log.info("开始下载文件：{}", url);

    Path tempFile = null;
    try {
      tempFile = Files.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
      final Path targetFile = tempFile;

      restClient
          .get()
          .uri(url)
          .exchange(
              (request, response) -> {
                if (response.getStatusCode().isError()) {
                  // 清理已创建的临时文件
                  Files.deleteIfExists(targetFile);
                  throw new FileDownloadException(
                      "下载失败，HTTP 状态码：" + response.getStatusCode().value(),
                      StandardErrorTrait.DEP_UNAVAILABLE);
                }
                try (InputStream is = response.getBody();
                    OutputStream os = Files.newOutputStream(targetFile)) {
                  is.transferTo(os);
                }
                return null;
              });

      long fileSize = Files.size(tempFile);
      log.info("文件下载完成：{}，大小：{} bytes", tempFile, fileSize);
      return tempFile;

    } catch (FileDownloadException e) {
      throw e; // 直接传递
    } catch (ResourceAccessException e) {
      // 网络超时等
      cleanupTempFile(tempFile);
      throw new FileDownloadException(
          "下载超时或网络不可达：" + e.getMessage(), e, StandardErrorTrait.TIMEOUT);
    } catch (IOException e) {
      cleanupTempFile(tempFile);
      throw new FileDownloadException(
          "下载文件时 IO 错误：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    } catch (Exception e) {
      cleanupTempFile(tempFile);
      throw new FileDownloadException(
          "下载文件时发生未知错误：" + e.getMessage(), e, StandardErrorTrait.DEP_UNAVAILABLE);
    }
  }

  private void cleanupTempFile(Path file) {
    if (file != null) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException e) {
        log.warn("清理临时文件失败：{}，原因：{}", file, e.getMessage());
      }
    }
  }
}
