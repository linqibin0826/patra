package com.patra.starter.restclient.download;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

/// DownloadClient 集成测试。
///
/// 使用 WireMock 模拟 HTTP 服务器，测试文件下载和进度回调。
///
/// @author linqibin
/// @since 0.1.0
@Timeout(30)
@WireMockTest
class DownloadClientIT {

  private DownloadClient downloadClient;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    RestClient restClient = RestClient.builder().baseUrl(wmRuntimeInfo.getHttpBaseUrl()).build();
    downloadClient = new DefaultDownloadClient(restClient);
  }

  @Nested
  @DisplayName("download 方法")
  class DownloadTests {

    @Test
    @DisplayName("应成功下载文件")
    void shouldDownloadFile(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      // 准备测试数据
      byte[] content = "Hello, World!".getBytes();
      wmRuntimeInfo
          .getWireMock()
          .register(
              get(urlEqualTo("/test.txt"))
                  .willReturn(
                      aResponse()
                          .withStatus(200)
                          .withHeader("Content-Length", String.valueOf(content.length))
                          .withBody(content)));

      // 执行下载
      Path targetPath = tempDir.resolve("test.txt");
      DownloadResult result =
          downloadClient.download(
              URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/test.txt"), targetPath, null);

      // 验证结果
      assertThat(result.filePath()).isEqualTo(targetPath);
      assertThat(result.fileSize()).isEqualTo(content.length);
      assertThat(Files.readString(targetPath)).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("应触发进度回调")
    void shouldTriggerProgressCallback(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      // 准备较大的测试数据以确保多次触发进度回调
      byte[] content = new byte[100 * 1024]; // 100KB
      for (int i = 0; i < content.length; i++) {
        content[i] = (byte) (i % 256);
      }

      wmRuntimeInfo
          .getWireMock()
          .register(
              get(urlEqualTo("/large.bin"))
                  .willReturn(
                      aResponse()
                          .withStatus(200)
                          .withHeader("Content-Length", String.valueOf(content.length))
                          .withBody(content)));

      // 记录进度回调
      List<DownloadProgress> progressUpdates = new ArrayList<>();
      ProgressListener listener =
          new ProgressListener() {
            @Override
            public void onProgress(DownloadProgress progress) {
              progressUpdates.add(progress);
            }

            @Override
            public void onComplete(DownloadProgress finalProgress) {
              progressUpdates.add(finalProgress);
            }
          };

      // 执行下载
      Path targetPath = tempDir.resolve("large.bin");
      DownloadResult result =
          downloadClient.download(
              URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/large.bin"), targetPath, listener);

      // 验证结果
      assertThat(result.fileSize()).isEqualTo(content.length);
      assertThat(progressUpdates).isNotEmpty();

      // 验证最终进度
      DownloadProgress finalProgress = progressUpdates.get(progressUpdates.size() - 1);
      assertThat(finalProgress.bytesDownloaded()).isEqualTo(content.length);
      assertThat(finalProgress.percentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("HTTP 错误应抛出异常")
    void shouldThrowOnHttpError(WireMockRuntimeInfo wmRuntimeInfo) {
      wmRuntimeInfo
          .getWireMock()
          .register(get(urlEqualTo("/not-found")).willReturn(aResponse().withStatus(404)));

      Path targetPath = tempDir.resolve("not-found.txt");

      assertThatThrownBy(
              () ->
                  downloadClient.download(
                      URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/not-found"), targetPath, null))
          .isInstanceOf(DownloadException.class)
          .hasMessageContaining("404");
    }

    @Test
    @DisplayName("应能处理未知大小的文件（无 Content-Length）")
    void shouldHandleUnknownContentLength(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      byte[] content = "Unknown size content".getBytes();
      wmRuntimeInfo
          .getWireMock()
          .register(
              get(urlEqualTo("/unknown-size.txt"))
                  .willReturn(
                      aResponse()
                          .withStatus(200)
                          // 不设置 Content-Length
                          .withBody(content)));

      // 记录进度
      List<DownloadProgress> progressUpdates = new ArrayList<>();
      ProgressListener listener = progress -> progressUpdates.add(progress);

      Path targetPath = tempDir.resolve("unknown-size.txt");
      DownloadResult result =
          downloadClient.download(
              URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/unknown-size.txt"),
              targetPath,
              listener);

      assertThat(result.fileSize()).isEqualTo(content.length);
      assertThat(Files.readString(targetPath)).isEqualTo("Unknown size content");
    }
  }

  @Nested
  @DisplayName("downloadToTemp 方法")
  class DownloadToTempTests {

    @Test
    @DisplayName("应下载到临时目录")
    void shouldDownloadToTempDirectory(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      byte[] content = "Temp file content".getBytes();
      wmRuntimeInfo
          .getWireMock()
          .register(
              get(urlEqualTo("/temp.txt"))
                  .willReturn(
                      aResponse()
                          .withStatus(200)
                          .withHeader("Content-Length", String.valueOf(content.length))
                          .withBody(content)));

      DownloadResult result =
          downloadClient.downloadToTemp(
              URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/temp.txt"), null);

      assertThat(result.filePath()).exists();
      assertThat(result.filePath().getFileName().toString()).startsWith("download-");
      assertThat(result.filePath().getFileName().toString()).endsWith(".tmp");
      assertThat(Files.readString(result.filePath())).isEqualTo("Temp file content");

      // 清理
      Files.deleteIfExists(result.filePath());
    }
  }

  @Nested
  @DisplayName("错误处理")
  class ErrorHandlingTests {

    @Test
    @DisplayName("应触发错误回调")
    void shouldTriggerErrorCallback(WireMockRuntimeInfo wmRuntimeInfo) {
      wmRuntimeInfo
          .getWireMock()
          .register(get(urlEqualTo("/error")).willReturn(aResponse().withStatus(500)));

      List<Exception> errors = new ArrayList<>();
      ProgressListener listener =
          new ProgressListener() {
            @Override
            public void onProgress(DownloadProgress progress) {}

            @Override
            public void onError(Exception exception, DownloadProgress lastProgress) {
              errors.add(exception);
            }
          };

      Path targetPath = tempDir.resolve("error.txt");

      assertThatThrownBy(
          () ->
              downloadClient.download(
                  URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/error"), targetPath, listener));

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).isInstanceOf(DownloadException.class);
    }
  }
}
