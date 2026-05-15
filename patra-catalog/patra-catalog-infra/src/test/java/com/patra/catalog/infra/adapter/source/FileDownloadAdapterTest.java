package com.patra.catalog.infra.adapter.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.port.source.FileDownloadResult;
import com.patra.starter.restclient.download.DownloadClient;
import com.patra.starter.restclient.download.DownloadException;
import com.patra.starter.restclient.download.DownloadOptions;
import com.patra.starter.restclient.download.DownloadProgress;
import com.patra.starter.restclient.download.DownloadResult;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// FileDownloadAdapter 单元测试。
///
/// 测试文件下载适配器的核心行为：
///
/// - 正常下载：委托 DownloadClient.downloadToTemp() 并映射结果
/// - FTP 场景：自动注入匿名凭证
/// - 异常映射：DownloadException → FileDownloadException
/// - 参数验证：null URL 抛出 NullPointerException
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("FileDownloadAdapter 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class FileDownloadAdapterTest {

  @Mock private DownloadClient downloadClient;

  private FileDownloadAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new FileDownloadAdapter(downloadClient);
  }

  /// 创建 DownloadResult Mock（模拟 Starter 层返回的落盘结果）。
  private DownloadResult createDownloadResult(Path filePath, long fileSize) {
    DownloadProgress progress = new DownloadProgress(fileSize, fileSize, 100, 1024, 0, 1000);
    return new DownloadResult(filePath, fileSize, progress);
  }

  @Nested
  @DisplayName("正常下载测试")
  class NormalDownloadTest {

    @Test
    @DisplayName("HTTP 下载成功 - 应该返回 FileDownloadResult 并包含文件路径和大小")
    void download_httpUrl_shouldReturnFileDownloadResult() {
      // Given
      Path tempFile = Path.of("/tmp/download-test-uuid.tmp");
      URI url = URI.create("https://example.com/data.xml");
      when(downloadClient.downloadToTemp(any(URI.class), isNull()))
          .thenReturn(createDownloadResult(tempFile, 1024));

      // When
      FileDownloadResult result = adapter.download(url);

      // Then
      assertThat(result.filePath()).isEqualTo(tempFile);
      assertThat(result.fileSize()).isEqualTo(1024);
      verify(downloadClient).downloadToTemp(url, null);
    }

    @Test
    @DisplayName("HTTP 下载 - 不应该传递 FTP 凭证")
    void download_httpUrl_shouldNotPassFtpCredentials() {
      // Given
      Path tempFile = Path.of("/tmp/download-test-uuid.tmp");
      URI url = URI.create("https://example.com/data.xml");
      when(downloadClient.downloadToTemp(any(URI.class), isNull()))
          .thenReturn(createDownloadResult(tempFile, 500));

      // When
      adapter.download(url);

      // Then: HTTP 场景 options 应该为 null
      verify(downloadClient).downloadToTemp(url, null);
    }
  }

  @Nested
  @DisplayName("FTP 下载测试")
  class FtpDownloadTest {

    @Test
    @DisplayName("FTP 下载 - 应该自动注入匿名凭证")
    void download_ftpUrl_shouldInjectAnonymousCredentials() {
      // Given
      Path tempFile = Path.of("/tmp/download-ftp-uuid.tmp");
      URI url = URI.create("ftp://ftp.nlm.nih.gov/pubmed/baseline/pubmed24n0001.xml.gz");
      ArgumentCaptor<DownloadOptions> optionsCaptor =
          ArgumentCaptor.forClass(DownloadOptions.class);
      when(downloadClient.downloadToTemp(any(URI.class), optionsCaptor.capture()))
          .thenReturn(createDownloadResult(tempFile, 2048));

      // When
      FileDownloadResult result = adapter.download(url);

      // Then
      assertThat(result.filePath()).isEqualTo(tempFile);
      DownloadOptions capturedOptions = optionsCaptor.getValue();
      assertThat(capturedOptions).isNotNull();
      assertThat(capturedOptions.ftpCredentials()).isNotNull();
      assertThat(capturedOptions.ftpCredentials().username()).isEqualTo("anonymous");
      assertThat(capturedOptions.ftpCredentials().password()).isEqualTo("patra@example.com");
    }
  }

  @Nested
  @DisplayName("异常映射测试")
  class ExceptionMappingTest {

    @Test
    @DisplayName("DownloadException - 应该映射为 FileDownloadException 并保留语义特征")
    void download_downloadException_shouldMapToFileDownloadException() {
      // Given
      URI url = URI.create("https://example.com/data.xml");
      DownloadException cause = new DownloadException("HTTP 404", StandardErrorTrait.NOT_FOUND);
      when(downloadClient.downloadToTemp(any(URI.class), isNull())).thenThrow(cause);

      // When & Then
      assertThatThrownBy(() -> adapter.download(url))
          .isInstanceOf(FileDownloadException.class)
          .hasCause(cause)
          .satisfies(
              e -> {
                FileDownloadException ex = (FileDownloadException) e;
                assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.NOT_FOUND);
              });
    }

    @Test
    @DisplayName("DownloadException 无语义特征 - 应该默认 DEP_UNAVAILABLE")
    void download_downloadExceptionNoTraits_shouldDefaultToDepUnavailable() {
      // Given
      URI url = URI.create("https://example.com/data.xml");
      DownloadException cause =
          new DownloadException("unknown error", StandardErrorTrait.DEP_UNAVAILABLE);
      when(downloadClient.downloadToTemp(any(URI.class), isNull())).thenThrow(cause);

      // When & Then
      assertThatThrownBy(() -> adapter.download(url))
          .isInstanceOf(FileDownloadException.class)
          .satisfies(
              e -> {
                FileDownloadException ex = (FileDownloadException) e;
                assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
              });
    }

    @Test
    @DisplayName("RuntimeException - 应该映射为 FileDownloadException 并携带 DEP_UNAVAILABLE")
    void download_runtimeException_shouldMapToFileDownloadException() {
      // Given
      URI url = URI.create("https://example.com/data.xml");
      when(downloadClient.downloadToTemp(any(URI.class), isNull()))
          .thenThrow(new RuntimeException("unexpected error"));

      // When & Then
      assertThatThrownBy(() -> adapter.download(url))
          .isInstanceOf(FileDownloadException.class)
          .hasMessageContaining("下载失败")
          .satisfies(
              e -> {
                FileDownloadException ex = (FileDownloadException) e;
                assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
              });
    }
  }

  @Nested
  @DisplayName("参数验证测试")
  class ParameterValidationTest {

    @Test
    @DisplayName("URL 为 null - 应该抛出 NullPointerException")
    void download_nullUrl_shouldThrowNullPointerException() {
      assertThatThrownBy(() -> adapter.download(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("下载 URL 不能为 null");
    }
  }
}
