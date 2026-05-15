package com.patra.starter.restclient.download;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.patra.starter.restclient.config.DownloadProperties;
import com.patra.starter.restclient.download.strategy.HttpStreamingDownloader;
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
import org.springframework.web.reactive.function.client.WebClient;

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
    WebClient webClient =
        WebClient.builder()
            .baseUrl(wmRuntimeInfo.getHttpBaseUrl())
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
            .build();
    DownloadProperties properties = new DownloadProperties();
    properties.setBaseDir(tempDir);
    properties.setTempDir(tempDir);
    downloadClient =
        new DefaultDownloadClient(
            List.of(new HttpStreamingDownloader(webClient, properties)), properties);
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
              URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/test.txt"),
              targetPath,
              DownloadOptions.defaultOptions());

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
              URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/large.bin"),
              targetPath,
              DownloadOptions.withProgressListener(listener));

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
                      URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/not-found"), targetPath))
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
              DownloadOptions.withProgressListener(listener));

      assertThat(result.fileSize()).isEqualTo(content.length);
      assertThat(Files.readString(targetPath)).isEqualTo("Unknown size content");
    }

    @Test
    @DisplayName("写入策略 SKIP - 已存在文件应直接返回并触发完成回调")
    void shouldSkipExistingFileAndNotifyComplete() throws Exception {
      Path targetPath = tempDir.resolve("skip.txt");
      Files.writeString(targetPath, "cached");

      List<DownloadProgress> updates = new ArrayList<>();
      ProgressListener listener =
          new ProgressListener() {
            @Override
            public void onProgress(DownloadProgress progress) {
              updates.add(progress);
            }

            @Override
            public void onComplete(DownloadProgress finalProgress) {
              updates.add(finalProgress);
            }
          };

      DownloadOptions options =
          new DownloadOptions(WriteStrategy.SKIP, null, null, null, null, listener);

      DownloadResult result =
          downloadClient.download(
              new DownloadRequest(URI.create("http://localhost/skip.txt"), targetPath, options));

      assertThat(result.filePath()).isEqualTo(targetPath);
      assertThat(Files.readString(targetPath)).isEqualTo("cached");
      assertThat(updates).isNotEmpty();
      assertThat(updates.get(updates.size() - 1).percentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("写入策略 FAIL - 已存在文件应抛出异常")
    void shouldFailWhenFileAlreadyExists() throws Exception {
      Path targetPath = tempDir.resolve("fail.txt");
      Files.writeString(targetPath, "cached");

      DownloadOptions options =
          new DownloadOptions(WriteStrategy.FAIL, null, null, null, null, null);

      assertThatThrownBy(
              () ->
                  downloadClient.download(
                      new DownloadRequest(
                          URI.create("http://localhost/fail.txt"), targetPath, options)))
          .isInstanceOf(DownloadException.class)
          .hasMessageContaining("目标文件已存在");
    }

    @Test
    @DisplayName("未指定目标路径 - 应使用 baseDir 生成文件名")
    void shouldUseBaseDirWhenTargetPathMissing(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      byte[] content = "base-dir".getBytes();
      wmRuntimeInfo
          .getWireMock()
          .register(
              get(urlEqualTo("/base-dir.txt"))
                  .willReturn(
                      aResponse()
                          .withStatus(200)
                          .withHeader("Content-Length", String.valueOf(content.length))
                          .withBody(content)));

      DownloadResult result =
          downloadClient.download(URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/base-dir.txt"));

      assertThat(result.filePath()).isEqualTo(tempDir.resolve("base-dir.txt"));
      assertThat(Files.readString(result.filePath())).isEqualTo("base-dir");
    }
  }

  @Nested
  @DisplayName("openStream 方法")
  class OpenStreamTests {

    @Test
    @DisplayName("应返回可读取的 InputStream")
    void shouldOpenStream(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      byte[] content = "stream-content".getBytes();
      wmRuntimeInfo
          .getWireMock()
          .register(
              get(urlEqualTo("/stream.txt"))
                  .willReturn(aResponse().withStatus(200).withBody(content)));

      try (StreamingDownloadResponse result =
          downloadClient.openStream(
              URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/stream.txt"), null)) {
        assertThat(new String(result.inputStream().readAllBytes())).isEqualTo("stream-content");
      }
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
                  URI.create(wmRuntimeInfo.getHttpBaseUrl() + "/error"),
                  targetPath,
                  DownloadOptions.withProgressListener(listener)));

      assertThat(errors).hasSize(1);
      assertThat(errors.get(0)).isInstanceOf(DownloadException.class);
    }
  }
}
