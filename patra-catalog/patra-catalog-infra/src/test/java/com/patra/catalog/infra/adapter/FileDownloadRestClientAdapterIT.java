package com.patra.catalog.infra.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.common.error.trait.StandardErrorTrait;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

/// FileDownloadRestClientAdapter 集成测试。
///
/// **测试策略**：
///
/// - 使用 WireMock 模拟 HTTP 服务器
/// - 验证正常下载、HTTP 错误、网络错误场景
/// - 验证临时文件创建和内容正确性
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("FileDownloadRestClientAdapter 集成测试")
class FileDownloadRestClientAdapterIT {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort()).build();

  private FileDownloadRestClientAdapter adapter;
  private Path downloadedFile;

  @BeforeEach
  void setUp() {
    RestClient restClient = RestClient.builder().build();
    adapter = new FileDownloadRestClientAdapter(restClient);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (downloadedFile != null && Files.exists(downloadedFile)) {
      Files.deleteIfExists(downloadedFile);
    }
  }

  @Nested
  @DisplayName("正常下载场景")
  class SuccessfulDownloadTest {

    @Test
    @DisplayName("下载成功 - 应该返回正确的本地文件路径")
    void download_shouldReturnLocalFilePath() throws Exception {
      // Given
      String fileContent = "<?xml version=\"1.0\"?><root>test content</root>";
      wireMock.stubFor(
          get("/mesh/desc2025.xml")
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/xml")
                      .withBody(fileContent)));

      URI url = URI.create(wireMock.baseUrl() + "/mesh/desc2025.xml");

      // When
      downloadedFile = adapter.downloadToTemp(url);

      // Then
      assertThat(downloadedFile).isNotNull();
      assertThat(Files.exists(downloadedFile)).isTrue();
      assertThat(Files.readString(downloadedFile)).isEqualTo(fileContent);
    }

    @Test
    @DisplayName("下载成功 - 文件名应该包含正确的前缀和后缀")
    void download_shouldUseCorrectFileNaming() throws Exception {
      // Given
      wireMock.stubFor(
          get("/mesh/test.xml").willReturn(aResponse().withStatus(200).withBody("test")));

      URI url = URI.create(wireMock.baseUrl() + "/mesh/test.xml");

      // When
      downloadedFile = adapter.downloadToTemp(url);

      // Then
      String fileName = downloadedFile.getFileName().toString();
      assertThat(fileName).startsWith("mesh-import-");
      assertThat(fileName).endsWith(".xml");
    }
  }

  @Nested
  @DisplayName("HTTP 错误场景")
  class HttpErrorTest {

    @Test
    @DisplayName("404 错误 - 应该抛出 FileDownloadException")
    void notFound_shouldThrowException() {
      // Given
      wireMock.stubFor(get("/mesh/notfound.xml").willReturn(aResponse().withStatus(404)));

      URI url = URI.create(wireMock.baseUrl() + "/mesh/notfound.xml");

      // When & Then
      assertThatThrownBy(() -> adapter.downloadToTemp(url))
          .isInstanceOf(FileDownloadException.class)
          .hasMessageContaining("404")
          .satisfies(
              ex -> {
                FileDownloadException fde = (FileDownloadException) ex;
                assertThat(fde.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
              });
    }

    @Test
    @DisplayName("500 错误 - 应该抛出 FileDownloadException")
    void serverError_shouldThrowException() {
      // Given
      wireMock.stubFor(get("/mesh/error.xml").willReturn(aResponse().withStatus(500)));

      URI url = URI.create(wireMock.baseUrl() + "/mesh/error.xml");

      // When & Then
      assertThatThrownBy(() -> adapter.downloadToTemp(url))
          .isInstanceOf(FileDownloadException.class)
          .hasMessageContaining("500")
          .satisfies(
              ex -> {
                FileDownloadException fde = (FileDownloadException) ex;
                assertThat(fde.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
              });
    }
  }

  @Nested
  @DisplayName("网络错误场景")
  class NetworkErrorTest {

    @Test
    @DisplayName("连接失败 - 应该抛出 FileDownloadException")
    void connectionFailed_shouldThrowException() {
      // Given - 连接到不存在的服务器
      URI url = URI.create("http://localhost:19999/mesh/timeout.xml");

      // When & Then
      assertThatThrownBy(() -> adapter.downloadToTemp(url))
          .isInstanceOf(FileDownloadException.class)
          .satisfies(
              ex -> {
                FileDownloadException fde = (FileDownloadException) ex;
                assertThat(fde.getErrorTraits())
                    .containsAnyOf(StandardErrorTrait.TIMEOUT, StandardErrorTrait.DEP_UNAVAILABLE);
              });
    }
  }
}
