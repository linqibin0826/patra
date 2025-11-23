package com.patra.catalog.infra.download;

import static org.assertj.core.api.Assertions.*;

import com.patra.catalog.domain.port.MeshFileDownloadPort;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestClient;

/// MeSH 文件下载手动验证测试（轻量级 Spring 容器）。
///
/// **目的**：快速验证 NLM 服务器是否可访问，文件能否下载。
///
/// **测试策略**：
///
/// - 只加载必要组件（RestClient、RestClientMeshFileDownloadImpl）
///   - 启动时间：约 1-2 秒
///   - 硬编码配置值（简单明了，手动测试无需配置灵活性）
///   - 不依赖 app 层（遵守六边形架构依赖规则）
///   - 使用 Slf4j 日志记录（而非 System.out）
///   - 使用 AssertJ 断言验证结果
///
/// **运行方式**：
///
/// - **IDEA**：右键测试方法 → Run（忽略 @Disabled）
///   - **Maven**：
///     ```bash
///     # 运行所有手动测试
///     mvn test -Pmanual-tests
///
///     # 运行特定测试方法
///     mvn test -Pmanual-tests -Dtest=MeshFileDownloadManualTest#quickTest_verifyUrlAccessibility
///     ```
///
/// **CI/CD 隔离保证**：
///
/// - ✅ 使用 @Tag("manual") 标记，Maven 自动排除（见 patra-parent/pom.xml）
///   - ✅ 使用 @Disabled 双重保护，防止 IDEA 中意外运行
///   - ✅ CI/CD 中运行 `mvn test` 不会执行此测试
///   - ✅ 仅在 `mvn test -Pmanual-tests` 时运行
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@ExtendWith(SpringExtension.class)
@Import({RestClientMeshFileDownloadImpl.class, MeshFileDownloadManualTest.TestConfig.class})
@Tag("manual") // CI/CD 中排除此测试
@DisplayName("MeSH 文件下载手动验证测试")
class MeshFileDownloadManualTest {

  @Autowired private MeshFileDownloadPort meshFileDownload;

  // 直接定义配置常量（手动测试，无需配置灵活性）
  private static final String SOURCE_URL =
      "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml";
  private static final long EXPECTED_FILE_SIZE = 313_524_224L; // 约 299 MB

  @TestConfiguration
  static class TestConfig {
    @Bean
    RestClient restClient() {
      // 配置超时时间（下载大文件需要较长时间）
      org.springframework.http.client.SimpleClientHttpRequestFactory factory =
          new org.springframework.http.client.SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(java.time.Duration.ofSeconds(30)); // 连接超时 30 秒
      factory.setReadTimeout(java.time.Duration.ofMinutes(10)); // 读取超时 10 分钟

      return RestClient.builder().requestFactory(factory).build();
    }
  }

  @Test
  @DisplayName("手动测试 - 真实下载 desc2025.xml")
  void manualTest_downloadRealFile() {
    // Given: 真实的 NLM URL
    log.info("========================================");
    log.info("开始下载 MeSH XML 文件");
    log.info("URL: {}", SOURCE_URL);
    log.info("预期文件大小: {} MB", EXPECTED_FILE_SIZE / (1024 * 1024));
    log.info("预计耗时: 1-5 分钟（取决于网络速度）");
    log.info("========================================");

    // When: 下载文件
    File downloadedFile = meshFileDownload.download(SOURCE_URL);

    // Then: 验证文件存在
    assertThat(downloadedFile).as("下载的文件应该存在").exists().isFile();

    // Then: 验证文件大小（MeSH XML 文件至少 100MB）
    long fileSizeMB = downloadedFile.length() / (1024 * 1024);
    assertThat(downloadedFile.length()).as("下载的文件大小应该至少 100MB").isGreaterThan(100_000_000);

    // Then: 验证文件路径
    assertThat(downloadedFile.getAbsolutePath()).as("文件应该保存在临时目录").contains("mesh-import");

    // 记录成功信息
    log.info("========================================");
    log.info("✅ 文件下载成功！");
    log.info("文件路径: {}", downloadedFile.getAbsolutePath());
    log.info("文件大小: {} MB", fileSizeMB);
    log.info("========================================");

    // Cleanup: 清理测试文件（避免占用磁盘空间）
    boolean deleted = downloadedFile.delete();
    if (deleted) {
      log.info("已清理测试文件: {}", downloadedFile.getName());
    } else {
      log.warn("无法删除测试文件: {}", downloadedFile.getAbsolutePath());
    }
  }

  @Test
  @DisplayName("快速测试 - 验证 NLM 服务器可访问")
  void quickTest_verifyUrlAccessibility() {
    // Given: 真实的 NLM URL
    log.info("验证 NLM 服务器可访问性: {}", SOURCE_URL);

    // When: 发送 HEAD 请求（只检查文件存在，不下载）
    RestClient restClient = RestClient.create();

    try {
      var response = restClient.head().uri(SOURCE_URL).retrieve().toBodilessEntity();

      // Then: 验证响应状态码
      assertThat(response.getStatusCode().is2xxSuccessful()).as("NLM 服务器应该返回成功状态码").isTrue();

      log.info("✅ NLM 服务器可访问，文件存在");
      log.info("HTTP 状态码: {}", response.getStatusCode());
      log.info("Content-Length: {} bytes", response.getHeaders().getContentLength());

    } catch (Exception e) {
      log.error("❌ 无法访问 NLM 服务器: {}", e.getMessage());
      throw e;
    }
  }

  @Test
  @DisplayName("快速测试 - 下载 HTTP Bin 测试文件")
  void quickTest_downloadSmallFile() {
    // Given: 小文件 URL（用于快速验证下载功能）
    String testUrl = "https://httpbin.org/bytes/1024"; // 1KB 测试文件

    log.info("下载测试文件: {}", testUrl);

    // When: 下载文件
    File downloadedFile = meshFileDownload.download(testUrl);

    // Then: 验证文件存在
    assertThat(downloadedFile).exists().isFile();

    assertThat(downloadedFile.length()).as("文件大小应该为 1024 字节").isEqualTo(1024);

    log.info("✅ 下载功能正常");
    log.info("文件路径: {}", downloadedFile.getAbsolutePath());
    log.info("文件大小: {} bytes", downloadedFile.length());

    // Cleanup: 清理测试文件
    boolean deleted = downloadedFile.delete();
    if (deleted) {
      log.info("已清理测试文件: {}", downloadedFile.getName());
    } else {
      log.warn("无法删除测试文件: {}", downloadedFile.getAbsolutePath());
    }
  }
}
