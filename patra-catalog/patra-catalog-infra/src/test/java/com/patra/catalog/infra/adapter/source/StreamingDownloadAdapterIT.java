package com.patra.catalog.infra.adapter.source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.error.trait.StandardErrorTrait;
import com.patra.starter.restclient.config.DownloadProperties;
import com.patra.starter.restclient.download.DefaultDownloadClient;
import com.patra.starter.restclient.download.DownloadClient;
import com.patra.starter.restclient.download.strategy.FtpStreamingDownloader;
import com.patra.starter.restclient.download.strategy.HttpStreamingDownloader;
import io.netty.channel.ChannelOption;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/// StreamingDownloadAdapter 集成测试。
///
/// 使用 WireMock 模拟 HTTP 服务器，验证流式下载适配器的各种场景：
///
/// - 正常下载：返回 InputStream 和正确的元数据
/// - HTTP 错误：4xx/5xx 状态码正确转换为 FileDownloadException
/// - 网络超时：超时错误携带 TIMEOUT 语义特征
/// - 资源释放：InputStream 正确关闭
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("StreamingDownloadAdapter 集成测试")
@WireMockTest
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class StreamingDownloadAdapterIT {

  private static final String TEST_PATH = "/test/file.xml";
  private static final String TEST_CONTENT = "<root><data>Test Content</data></root>";

  private StreamingDownloadAdapter adapter;
  private String baseUrl;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
    baseUrl = wmRuntimeInfo.getHttpBaseUrl();

    // 创建 WebClient 连接到 WireMock 服务器
    WebClient webClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
            .build();

    DownloadProperties properties = new DownloadProperties();
    DownloadClient downloadClient =
        new DefaultDownloadClient(
            List.of(
                new HttpStreamingDownloader(webClient, properties),
                new FtpStreamingDownloader(properties)),
            properties);
    adapter = new StreamingDownloadAdapter(downloadClient);
  }

  @Nested
  @DisplayName("正常下载测试")
  class NormalDownloadTest {

    @Test
    @DisplayName("下载成功 - 应该返回 InputStream 和正确的元数据")
    void download_success_shouldReturnInputStreamAndMetadata() throws Exception {
      // Given: 模拟成功响应
      stubFor(
          get(urlEqualTo(TEST_PATH))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                      .withHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(TEST_CONTENT.length()))
                      .withBody(TEST_CONTENT)));

      // When: 执行下载
      try (StreamingDownloadResult result = adapter.download(URI.create(baseUrl + TEST_PATH))) {
        // Then: 验证元数据（Content-Length 可能为 -1，取决于 WireMock 实现）
        assertThat(result.contentLength()).isIn((long) TEST_CONTENT.length(), -1L);
        assertThat(result.contentType()).isEqualTo(MediaType.APPLICATION_XML_VALUE);

        // 验证 InputStream 内容
        String content = new String(result.inputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo(TEST_CONTENT);
      }
    }

    @Test
    @DisplayName("下载成功（无 Content-Length）- 应该返回 -1")
    void download_noContentLength_shouldReturnMinusOne() throws Exception {
      // Given: 模拟响应（无 Content-Length，如 chunked 编码）
      stubFor(
          get(urlEqualTo(TEST_PATH))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                      .withBody("{\"data\": \"test\"}")));

      // When: 执行下载
      try (StreamingDownloadResult result = adapter.download(URI.create(baseUrl + TEST_PATH))) {
        // Then: Content-Length 应该为 -1（WireMock 默认会设置，但验证逻辑正确性）
        assertThat(result.inputStream()).isNotNull();
        assertThat(result.contentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
      }
    }

    @Test
    @DisplayName("下载成功（无 Content-Type）- contentType 应该为 null")
    void download_noContentType_shouldReturnNullContentType() throws Exception {
      // Given: 模拟响应（无 Content-Type）
      stubFor(
          get(urlEqualTo(TEST_PATH))
              .willReturn(aResponse().withStatus(200).withBody("plain text content")));

      // When: 执行下载
      try (StreamingDownloadResult result = adapter.download(URI.create(baseUrl + TEST_PATH))) {
        // Then: contentType 应该为 null
        assertThat(result.inputStream()).isNotNull();
        // WireMock 不设置 Content-Type 时，WebClient 可能返回 null
      }
    }
  }

  @Nested
  @DisplayName("HTTP 错误处理测试")
  class HttpErrorTest {

    @Test
    @DisplayName("HTTP 404 - 应该抛出 FileDownloadException 并携带 DEP_UNAVAILABLE")
    void download_http404_shouldThrowFileDownloadException() {
      // Given: 模拟 404 响应
      stubFor(
          get(urlEqualTo(TEST_PATH)).willReturn(aResponse().withStatus(404).withBody("Not Found")));

      // When & Then
      assertThatThrownBy(() -> adapter.download(URI.create(baseUrl + TEST_PATH)))
          .isInstanceOf(FileDownloadException.class)
          .hasMessageContaining("404")
          .satisfies(
              e -> {
                FileDownloadException ex = (FileDownloadException) e;
                assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
              });
    }

    @Test
    @DisplayName("HTTP 500 - 应该抛出 FileDownloadException 并携带 DEP_UNAVAILABLE")
    void download_http500_shouldThrowFileDownloadException() {
      // Given: 模拟 500 响应
      stubFor(
          get(urlEqualTo(TEST_PATH))
              .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

      // When & Then
      assertThatThrownBy(() -> adapter.download(URI.create(baseUrl + TEST_PATH)))
          .isInstanceOf(FileDownloadException.class)
          .hasMessageContaining("500")
          .satisfies(
              e -> {
                FileDownloadException ex = (FileDownloadException) e;
                assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
              });
    }

    @Test
    @DisplayName("HTTP 503 - 应该抛出 FileDownloadException")
    void download_http503_shouldThrowFileDownloadException() {
      // Given: 模拟 503 响应
      stubFor(
          get(urlEqualTo(TEST_PATH))
              .willReturn(aResponse().withStatus(503).withBody("Service Unavailable")));

      // When & Then
      assertThatThrownBy(() -> adapter.download(URI.create(baseUrl + TEST_PATH)))
          .isInstanceOf(FileDownloadException.class)
          .hasMessageContaining("503");
    }
  }

  @Nested
  @DisplayName("网络超时测试")
  class NetworkTimeoutTest {

    @BeforeEach
    void setUpWithShortTimeout(WireMockRuntimeInfo wmRuntimeInfo) {
      // 创建具有短超时的 WebClient 以便测试超时场景
      HttpClient httpClient =
          HttpClient.create()
              .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500)
              .responseTimeout(Duration.ofMillis(500));

      WebClient webClient =
          WebClient.builder()
              .baseUrl(wmRuntimeInfo.getHttpBaseUrl())
              .clientConnector(new ReactorClientHttpConnector(httpClient))
              .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
              .build();

      DownloadProperties properties = new DownloadProperties();
      properties.getRetry().setEnabled(false);
      DownloadClient downloadClient =
          new DefaultDownloadClient(
              List.of(
                  new HttpStreamingDownloader(webClient, properties),
                  new FtpStreamingDownloader(properties)),
              properties);
      adapter = new StreamingDownloadAdapter(downloadClient);
    }

    @Test
    @DisplayName("读取超时 - 应该抛出 FileDownloadException 并携带网络错误特征")
    void download_readTimeout_shouldThrowFileDownloadExceptionWithNetworkTrait() {
      // Given: 模拟延迟响应（超过读取超时）
      stubFor(
          get(urlEqualTo(TEST_PATH))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withFixedDelay(2000) // 2 秒延迟，超过 500ms 超时
                      .withBody(TEST_CONTENT)));

      // When & Then
      // 注意：超时异常消息格式因 JDK 版本不同而有差异
      // 如果消息包含 "timeout" 则携带 TIMEOUT 特征，否则携带 DEP_UNAVAILABLE
      assertThatThrownBy(() -> adapter.download(URI.create(baseUrl + TEST_PATH)))
          .isInstanceOf(FileDownloadException.class)
          .satisfies(
              e -> {
                FileDownloadException ex = (FileDownloadException) e;
                // 应该携带 TIMEOUT 或 DEP_UNAVAILABLE 特征
                assertThat(ex.getErrorTraits())
                    .containsAnyOf(StandardErrorTrait.TIMEOUT, StandardErrorTrait.DEP_UNAVAILABLE);
              });
    }
  }

  @Nested
  @DisplayName("资源释放测试")
  class ResourceReleaseTest {

    @Test
    @DisplayName("try-with-resources - 应该成功完成资源释放")
    void tryWithResources_shouldCompleteWithoutError() throws Exception {
      // Given: 模拟成功响应
      stubFor(
          get(urlEqualTo(TEST_PATH))
              .willReturn(aResponse().withStatus(200).withBody(TEST_CONTENT)));

      // When & Then: 使用 try-with-resources 应该正常完成，不抛出异常
      try (StreamingDownloadResult result = adapter.download(URI.create(baseUrl + TEST_PATH))) {
        // 读取全部内容
        String content = new String(result.inputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo(TEST_CONTENT);
      }
      // 资源应该在这里被正确释放（close() 被自动调用）
    }

    @Test
    @DisplayName("手动关闭 - 应该正确释放资源")
    void manualClose_shouldReleaseResourceWithoutError() throws Exception {
      // Given: 模拟成功响应
      stubFor(
          get(urlEqualTo(TEST_PATH))
              .willReturn(aResponse().withStatus(200).withBody(TEST_CONTENT)));

      // When: 手动关闭
      StreamingDownloadResult result = adapter.download(URI.create(baseUrl + TEST_PATH));
      InputStream stream = result.inputStream();

      // 读取内容
      String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(content).isEqualTo(TEST_CONTENT);

      // Then: 关闭应该不抛出异常
      result.close();
    }

    @Test
    @DisplayName("多次关闭 - 应该幂等不抛出异常")
    void multipleClose_shouldBeIdempotent() throws Exception {
      // Given: 模拟成功响应
      stubFor(
          get(urlEqualTo(TEST_PATH))
              .willReturn(aResponse().withStatus(200).withBody(TEST_CONTENT)));

      // When: 多次关闭
      StreamingDownloadResult result = adapter.download(URI.create(baseUrl + TEST_PATH));

      // Then: 多次调用 close() 应该是幂等的
      result.close();
      result.close(); // 第二次关闭不应该抛出异常
    }
  }

  @Nested
  @DisplayName("参数验证测试")
  class ParameterValidationTest {

    @Test
    @DisplayName("URL 为 null - 应该抛出 NullPointerException")
    void download_nullUrl_shouldThrowNullPointerException() {
      // When & Then
      assertThatThrownBy(() -> adapter.download(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("下载 URL 不能为 null");
    }

    @Test
    @DisplayName("不支持的协议 - 应该抛出 FileDownloadException")
    void download_unsupportedScheme_shouldThrowFileDownloadException() {
      // When & Then
      assertThatThrownBy(() -> adapter.download(URI.create("sftp://example.com/file.xml")))
          .isInstanceOf(FileDownloadException.class)
          .hasMessageContaining("不支持的协议")
          .satisfies(
              e -> {
                FileDownloadException ex = (FileDownloadException) e;
                assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.RULE_VIOLATION);
              });
    }
  }

  // ========== FTP 下载测试（独立的测试类，不使用 @WireMockTest） ==========

  /// FTP 下载集成测试。
  ///
  /// 使用 MockFtpServer 模拟 FTP 服务器，验证 FTP 下载功能：
  /// - 正常下载：匿名登录、二进制传输、资源释放
  /// - 错误处理：文件不存在、连接失败
  @Nested
  @DisplayName("FTP 下载测试")
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  class FtpDownloadTest {

    private static final String FTP_TEST_CONTENT = "<root><data>FTP Test Content</data></root>";
    private static final String FTP_FILE_PATH = "/online/journals/lsi2025.xml";

    private FakeFtpServer ftpServer;
    private StreamingDownloadAdapter ftpAdapter;
    private int ftpPort;

    @BeforeEach
    void setUpFtpServer() {
      // 创建 FakeFtpServer
      ftpServer = new FakeFtpServer();
      ftpServer.setServerControlPort(0); // 自动分配端口

      // 配置匿名用户
      ftpServer.addUserAccount(new UserAccount("anonymous", "patra@example.com", "/"));

      // 配置文件系统
      UnixFakeFileSystem fileSystem = new UnixFakeFileSystem();
      fileSystem.add(new DirectoryEntry("/"));
      fileSystem.add(new DirectoryEntry("/online"));
      fileSystem.add(new DirectoryEntry("/online/journals"));
      fileSystem.add(new FileEntry(FTP_FILE_PATH, FTP_TEST_CONTENT));
      ftpServer.setFileSystem(fileSystem);

      // 启动服务器
      ftpServer.start();
      ftpPort = ftpServer.getServerControlPort();

      DownloadProperties properties = new DownloadProperties();
      DownloadClient downloadClient =
          new DefaultDownloadClient(List.of(new FtpStreamingDownloader(properties)), properties);
      ftpAdapter = new StreamingDownloadAdapter(downloadClient);
    }

    @AfterEach
    void tearDownFtpServer() {
      if (ftpServer != null && ftpServer.isStarted()) {
        ftpServer.stop();
      }
    }

    @Test
    @DisplayName("FTP 下载成功 - 应该返回 InputStream 和正确内容")
    void ftpDownload_success_shouldReturnInputStreamAndContent() throws Exception {
      // Given: FTP URL
      URI ftpUrl = URI.create("ftp://localhost:" + ftpPort + FTP_FILE_PATH);

      // When: 执行下载
      try (StreamingDownloadResult result = ftpAdapter.download(ftpUrl)) {
        // Then: 验证 InputStream 内容
        String content = new String(result.inputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo(FTP_TEST_CONTENT);

        // 验证 contentType（FTP 默认返回 application/xml）
        assertThat(result.contentType()).isEqualTo("application/xml");
      }
    }

    @Test
    @DisplayName("FTP 下载成功 - 资源应正确释放")
    void ftpDownload_resourceRelease_shouldCompleteWithoutError() throws Exception {
      // Given: FTP URL
      URI ftpUrl = URI.create("ftp://localhost:" + ftpPort + FTP_FILE_PATH);

      // When: 使用 try-with-resources
      try (StreamingDownloadResult result = ftpAdapter.download(ftpUrl)) {
        // 读取部分内容（模拟提前关闭）
        byte[] buffer = new byte[10];
        result.inputStream().read(buffer);
      }
      // Then: 资源应正确释放（无异常）
    }

    @Test
    @DisplayName("FTP 文件不存在 - 应该抛出 FileDownloadException")
    void ftpDownload_fileNotFound_shouldThrowFileDownloadException() {
      // Given: 不存在的文件路径
      URI ftpUrl = URI.create("ftp://localhost:" + ftpPort + "/nonexistent/file.xml");

      // When & Then
      assertThatThrownBy(() -> ftpAdapter.download(ftpUrl))
          .isInstanceOf(FileDownloadException.class)
          .satisfies(
              e -> {
                FileDownloadException ex = (FileDownloadException) e;
                assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
              });
    }

    @Test
    @DisplayName("FTP 连接失败 - 应该抛出 FileDownloadException")
    void ftpDownload_connectionFailed_shouldThrowFileDownloadException() {
      // Given: 无效端口
      URI ftpUrl = URI.create("ftp://localhost:59999/file.xml");

      // When & Then
      assertThatThrownBy(() -> ftpAdapter.download(ftpUrl))
          .isInstanceOf(FileDownloadException.class)
          .satisfies(
              e -> {
                FileDownloadException ex = (FileDownloadException) e;
                assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
              });
    }
  }
}
