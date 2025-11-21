package com.patra.catalog.infra.download;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import cn.hutool.crypto.digest.DigestUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

/**
 * RestClient 文件下载器单元测试。
 *
 * <p>使用 WireMock 模拟 HTTP 响应，测试文件下载和校验功能。
 *
 * <p><b>测试策略</b>：
 *
 * <ul>
 *   <li>单元测试：使用 WireMock 模拟 HTTP 服务器
 *   <li>测试覆盖：download()、validateChecksum()、超时处理
 *   <li>边界情况：网络错误、文件损坏、校验失败
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("RestClientMeshFileDownloadImpl 单元测试")
class RestClientMeshFileDownloadImplTest {

  private static WireMockServer wireMockServer;

  private RestClientMeshFileDownloadImpl meshFileDownload;

  @BeforeAll
  static void setupClass() {
    // 启动 WireMock 服务器
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    configureFor("localhost", wireMockServer.port());
  }

  @AfterAll
  static void teardownClass() {
    // 停止 WireMock 服务器
    wireMockServer.stop();
  }

  @BeforeEach
  void setup() {
    // 重置 WireMock 状态
    wireMockServer.resetAll();

    // 创建 RestClient
    RestClient restClient = RestClient.builder().baseUrl(wireMockServer.baseUrl()).build();

    // 创建下载器（注入 RestClient）
    meshFileDownload = new RestClientMeshFileDownloadImpl(restClient);
  }

  @Test
  @DisplayName("下载文件 - 应该成功下载并保存到本地")
  void download_validUrl_shouldDownloadFile() throws IOException {
    // Given: Mock HTTP 响应
    String xmlContent = "<DescriptorRecordSet><DescriptorRecord>test</DescriptorRecord></DescriptorRecordSet>";
    stubFor(
        get(urlEqualTo("/mesh/desc2025.xml"))
            .willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/xml").withBody(xmlContent)));

    // When: 下载文件
    String sourceUrl = wireMockServer.url("/mesh/desc2025.xml");
    File downloadedFile = meshFileDownload.download(sourceUrl);

    // Then: 文件应该存在
    assertThat(downloadedFile).exists().isFile();

    // Then: 文件内容应该正确
    String content = Files.readString(downloadedFile.toPath());
    assertThat(content).isEqualTo(xmlContent);

    // 清理
    downloadedFile.delete();
  }

  @Test
  @DisplayName("下载文件 - 服务器返回404时应该抛出异常")
  void download_serverReturns404_shouldThrowException() {
    // Given: Mock 404 响应
    stubFor(get(urlEqualTo("/mesh/desc2025.xml")).willReturn(aResponse().withStatus(404)));

    // When & Then: 应该抛出异常
    String sourceUrl = wireMockServer.url("/mesh/desc2025.xml");
    assertThatThrownBy(() -> meshFileDownload.download(sourceUrl))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("下载失败");
  }

  @Test
  @DisplayName("下载文件 - 网络超时时应该抛出异常")
  void download_networkTimeout_shouldThrowException() {
    // Given: Mock 延迟响应（模拟超时）
    stubFor(
        get(urlEqualTo("/mesh/desc2025.xml"))
            .willReturn(aResponse().withStatus(200).withFixedDelay(10000))); // 10秒延迟

    // When & Then: 应该抛出超时异常
    String sourceUrl = wireMockServer.url("/mesh/desc2025.xml");
    assertThatThrownBy(() -> meshFileDownload.download(sourceUrl))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("验证文件校验和 - 校验成功时应该返回true")
  void validateChecksum_validHash_shouldReturnTrue() throws IOException {
    // Given: 创建测试文件
    String content = "test content";
    File tempFile = Files.createTempFile("mesh-test", ".xml").toFile();
    Files.writeString(tempFile.toPath(), content);

    // Given: 计算MD5
    String expectedHash = DigestUtil.md5Hex(content);

    // When: 验证校验和
    boolean valid = meshFileDownload.validateChecksum(tempFile, expectedHash);

    // Then: 应该返回true
    assertThat(valid).isTrue();

    // 清理
    tempFile.delete();
  }

  @Test
  @DisplayName("验证文件校验和 - 校验失败时应该返回false")
  void validateChecksum_invalidHash_shouldReturnFalse() throws IOException {
    // Given: 创建测试文件
    String content = "test content";
    File tempFile = Files.createTempFile("mesh-test", ".xml").toFile();
    Files.writeString(tempFile.toPath(), content);

    // Given: 错误的MD5
    String wrongHash = "wronghash123456789";

    // When: 验证校验和
    boolean valid = meshFileDownload.validateChecksum(tempFile, wrongHash);

    // Then: 应该返回false
    assertThat(valid).isFalse();

    // 清理
    tempFile.delete();
  }

  @Test
  @DisplayName("验证文件校验和 - 文件不存在时应该抛出异常")
  void validateChecksum_fileNotExists_shouldThrowException() {
    // Given: 不存在的文件
    File nonExistentFile = new File("/tmp/non-existent-file.xml");

    // When & Then: 应该抛出异常
    assertThatThrownBy(() -> meshFileDownload.validateChecksum(nonExistentFile, "somehash"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("文件不存在");
  }

  @Test
  @DisplayName("下载大文件 - 应该支持流式下载")
  void download_largeFile_shouldSupportStreaming() throws IOException {
    // Given: Mock 大文件响应（10MB）
    byte[] largeContent = new byte[10 * 1024 * 1024]; // 10MB
    stubFor(
        get(urlEqualTo("/mesh/large.xml"))
            .willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/xml").withBody(largeContent)));

    // When: 下载大文件
    String sourceUrl = wireMockServer.url("/mesh/large.xml");
    File downloadedFile = meshFileDownload.download(sourceUrl);

    // Then: 文件应该存在且大小正确
    assertThat(downloadedFile).exists().isFile();
    assertThat(downloadedFile.length()).isEqualTo(largeContent.length);

    // 清理
    downloadedFile.delete();
  }

  @Test
  @DisplayName("下载文件 - URL为空时应该抛出异常")
  void download_emptyUrl_shouldThrowException() {
    // When & Then: 应该抛出异常
    assertThatThrownBy(() -> meshFileDownload.download(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URL 不能为空");
  }

  @Test
  @DisplayName("下载文件 - URL格式错误时应该抛出异常")
  void download_invalidUrl_shouldThrowException() {
    // When & Then: 应该抛出异常
    assertThatThrownBy(() -> meshFileDownload.download("not-a-valid-url"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URL 格式错误");
  }
}
