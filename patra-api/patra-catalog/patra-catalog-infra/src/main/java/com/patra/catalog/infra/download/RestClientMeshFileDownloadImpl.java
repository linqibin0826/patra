package com.patra.catalog.infra.download;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.patra.catalog.domain.port.MeshFileDownloadPort;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/// RestClient 文件下载器实现。
/// 
/// 使用 Spring RestClient（底层 JDK 21 HttpClient）实现 MeSH 文件下载和校验。
/// 
/// **设计原则**：
/// 
/// - 流式下载：支持大文件（700MB+），不一次性加载到内存
///   - 断点续传：支持 HTTP Range 请求（TODO: 后续实现）
///   - 超时控制：通过 RestClient 配置超时时间
///   - 校验完整性：使用 MD5 哈希验证文件完整性
/// 
/// **性能特征**：
/// 
/// - 下载速度：取决于网络带宽（约 10-50 MB/s）
///   - 内存占用：流式写入，内存占用可控（<100MB）
///   - 文件大小：支持 700MB+ 的 XML 文件
/// 
/// @author linqibin
/// @since 0.2.0
@Slf4j
@Component
@RequiredArgsConstructor
public class RestClientMeshFileDownloadImpl implements MeshFileDownloadPort {

  private final RestClient restClient;

  /// 临时下载目录
  private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/mesh-import";

  @Override
  public File download(String sourceUrl) {
    log.info("开始下载 MeSH XML 文件: {}", sourceUrl);

    // 1. 验证 URL
    if (StrUtil.isBlank(sourceUrl)) {
      throw new IllegalArgumentException("URL 不能为空");
    }
    if (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://")) {
      throw new IllegalArgumentException("URL 格式错误，必须以 http:// 或 https:// 开头");
    }

    // 2. 创建临时目录
    File tempDir = new File(TEMP_DIR);
    if (!tempDir.exists()) {
      boolean created = tempDir.mkdirs();
      if (!created) {
        throw new RuntimeException("创建临时目录失败: " + TEMP_DIR);
      }
      log.info("创建临时目录: {}", TEMP_DIR);
    }

    // 3. 生成临时文件名
    String fileName = FileUtil.getName(sourceUrl);
    File tempFile = new File(tempDir, fileName);
    log.info("下载目标文件: {}", tempFile.getAbsolutePath());

    // 4. 下载文件（流式写入）
    try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
      restClient
          .get()
          .uri(sourceUrl)
          .exchange(
              (request, response) -> {
                // 获取响应输入流
                try (InputStream inputStream = response.getBody()) {
                  // 流式复制（使用已定义的方法）
                  copyStream(inputStream, outputStream);
                  log.info("文件下载成功，文件大小: {} bytes", tempFile.length());
                  return tempFile;
                } catch (IOException e) {
                  log.error("文件写入失败: {}", tempFile, e);
                  throw new RuntimeException("磁盘写入失败: " + e.getMessage(), e);
                }
              });

      return tempFile;
    } catch (org.springframework.web.client.HttpClientErrorException e) {
      // 4xx 客户端错误
      log.error("HTTP 客户端错误（{}）: {}", e.getStatusCode(), sourceUrl, e);
      throw new RuntimeException("HTTP 请求失败: " + e.getMessage(), e);
    } catch (org.springframework.web.client.HttpServerErrorException e) {
      // 5xx 服务器错误
      log.error("HTTP 服务器错误（{}）: {}", e.getStatusCode(), sourceUrl, e);
      throw new RuntimeException("服务器错误，稍后重试: " + e.getMessage(), e);
    } catch (org.springframework.web.client.ResourceAccessException e) {
      // 网络超时、连接失败
      log.error("网络访问失败: {}", sourceUrl, e);
      throw new RuntimeException("网络不可达或超时: " + e.getMessage(), e);
    } catch (IOException e) {
      log.error("文件IO失败: {}", tempFile, e);
      throw new RuntimeException("文件操作失败: " + e.getMessage(), e);
    } catch (Exception e) {
      log.error("下载文件失败（未知异常）: {}", sourceUrl, e);
      throw new RuntimeException("下载失败: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean validateChecksum(File xmlFile, String expectedHash) {
    log.info("验证文件校验和: {}", xmlFile.getName());

    // 1. 验证文件存在
    if (!xmlFile.exists()) {
      throw new IllegalArgumentException("文件不存在: " + xmlFile.getAbsolutePath());
    }

    // 2. 验证哈希格式
    if (StrUtil.isBlank(expectedHash)) {
      throw new IllegalArgumentException("预期哈希值不能为空");
    }

    // 3. 计算文件 MD5
    try {
      String actualHash = DigestUtil.md5Hex(xmlFile);
      log.info("文件 MD5: {}", actualHash);

      // 4. 比较哈希值（忽略大小写）
      boolean valid = actualHash.equalsIgnoreCase(expectedHash);
      if (valid) {
        log.info("文件校验成功");
      } else {
        log.warn("文件校验失败，期望: {}, 实际: {}", expectedHash, actualHash);
      }
      return valid;
    } catch (Exception e) {
      log.error("计算文件哈希失败", e);
      throw new RuntimeException("计算哈希失败: " + e.getMessage(), e);
    }
  }

  /// 复制输入流到输出流（流式处理，含性能监控）。
/// 
/// 使用固定大小缓冲区（8KB）流式复制数据，避免大文件导致 OOM。
/// 
/// 性能特征：
/// 
/// - 内存占用：稳定在 8KB（缓冲区大小）
///   - 下载速度：取决于网络带宽和磁盘 IO
///   - 日志频率：每 10MB 或每 5 秒记录一次进度
/// 
/// @param inputStream 输入流
/// @param outputStream 输出流
/// @throws IOException IO 异常
  private void copyStream(InputStream inputStream, FileOutputStream outputStream)
      throws IOException {
    byte[] buffer = new byte[8192]; // 8KB 缓冲区
    int bytesRead;
    long totalBytes = 0;
    long startTime = System.currentTimeMillis();
    long lastLogTime = startTime;

    while ((bytesRead = inputStream.read(buffer)) != -1) {
      outputStream.write(buffer, 0, bytesRead);
      totalBytes += bytesRead;

      // 每 10MB 或每 5 秒记录一次进度（避免日志过多）
      long currentTime = System.currentTimeMillis();
      if (totalBytes % (10 * 1024 * 1024) == 0 || (currentTime - lastLogTime) >= 5000) {
        long elapsedSeconds = (currentTime - startTime) / 1000;
        double speedMBps =
            elapsedSeconds > 0 ? (totalBytes / (1024.0 * 1024.0)) / elapsedSeconds : 0;

        if (log.isInfoEnabled()) {
          log.info(
              "下载进度: {} MB（速度: {:.2f} MB/s）",
              totalBytes / (1024 * 1024),
              speedMBps);
        }
        lastLogTime = currentTime;
      }
    }

    // 最终统计
    long totalTime = System.currentTimeMillis() - startTime;
    double averageSpeedMBps =
        totalTime > 0 ? (totalBytes / (1024.0 * 1024.0)) / (totalTime / 1000.0) : 0;

    log.info(
        "下载完成，总大小: {} MB，耗时: {} 秒，平均速度: {:.2f} MB/s",
        totalBytes / (1024 * 1024),
        totalTime / 1000,
        averageSpeedMBps);
  }
}
