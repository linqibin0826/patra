package dev.linqibin.patra.catalog.infra.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.error.trait.StandardErrorTrait;
import dev.linqibin.patra.catalog.domain.exception.FileDownloadException;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadResult;
import dev.linqibin.starter.objectstorage.ObjectStorageOperations;
import dev.linqibin.starter.objectstorage.domain.ObjectMetadata;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VenueCoverImageDownloadAdapter 单元测试。
///
/// 验证下载 + 上传 + 临时文件清理的完整生命周期，以及各种异常路径的语义特征。
@ExtendWith(MockitoExtension.class)
@DisplayName("VenueCoverImageDownloadAdapter 单元测试")
class VenueCoverImageDownloadAdapterTest {

  @Mock private FileDownloadPort fileDownloadPort;
  @Mock private ObjectStorageOperations objectStorage;

  private VenueCoverImageProperties properties;
  private VenueCoverImageDownloadAdapter adapter;

  @BeforeEach
  void setUp() {
    properties = new VenueCoverImageProperties("patra-catalog");
    adapter = new VenueCoverImageDownloadAdapter(fileDownloadPort, objectStorage, properties);
  }

  @Test
  @DisplayName("下载成功后应上传并返回对象键，并删除临时文件")
  void shouldDownloadAndUploadThenReturnObjectKey() throws IOException {
    // Given
    URI sourceUrl =
        URI.create(
            "https://media-cdn.oss-cn-hangzhou.aliyuncs.com/statics/images/comment_center/cover/journal/6054.jpg");
    String targetKey = "catalog/venue-cover/1.jpg";
    Path tempFile = Files.createTempFile("cover-test-", ".jpg");
    Files.writeString(tempFile, "fake-image-bytes");
    long fileSize = Files.size(tempFile);

    when(fileDownloadPort.download(sourceUrl))
        .thenReturn(FileDownloadResult.of(tempFile, fileSize));

    // When
    String result = adapter.downloadAndStore(sourceUrl, targetKey);

    // Then
    assertThat(result).isEqualTo(targetKey);
    assertThat(Files.exists(tempFile)).as("临时文件应在 finally 中被清理").isFalse();

    ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
    verify(objectStorage)
        .upload(eq("patra-catalog"), eq(targetKey), any(), metadataCaptor.capture());
    assertThat(metadataCaptor.getValue().getContentType()).isEqualTo("image/jpeg");
    assertThat(metadataCaptor.getValue().getContentLength()).isEqualTo(fileSize);
  }

  @Test
  @DisplayName("下载的文件大小为 0 时应抛出 FileDownloadException(DEP_UNAVAILABLE)")
  void shouldThrowWhenDownloadedFileIsEmpty() throws IOException {
    // Given
    URI sourceUrl = URI.create("https://example.com/empty.jpg");
    String targetKey = "catalog/venue-cover/2.jpg";
    Path tempFile = Files.createTempFile("cover-empty-", ".jpg");
    when(fileDownloadPort.download(sourceUrl)).thenReturn(FileDownloadResult.of(tempFile, 0L));

    // When
    FileDownloadException ex =
        catchThrowableOfType(
            () -> adapter.downloadAndStore(sourceUrl, targetKey), FileDownloadException.class);

    // Then
    assertThat(ex).isNotNull();
    assertThat(ex.getMessage()).contains("封面响应为空");
    assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
    assertThat(Files.exists(tempFile)).as("临时文件仍需清理").isFalse();
  }

  @Test
  @DisplayName("下载的文件大小超过 16 MiB 时应抛出 FileDownloadException(RULE_VIOLATION)")
  void shouldThrowWhenDownloadedFileExceedsMaxBytes() throws IOException {
    // Given
    URI sourceUrl = URI.create("https://example.com/huge.jpg");
    String targetKey = "catalog/venue-cover/3.jpg";
    Path tempFile = Files.createTempFile("cover-huge-", ".jpg");
    long hugeSize = 17L * 1024 * 1024;
    when(fileDownloadPort.download(sourceUrl))
        .thenReturn(FileDownloadResult.of(tempFile, hugeSize));

    // When
    FileDownloadException ex =
        catchThrowableOfType(
            () -> adapter.downloadAndStore(sourceUrl, targetKey), FileDownloadException.class);

    // Then
    assertThat(ex).isNotNull();
    assertThat(ex.getMessage()).contains("封面大小超限");
    assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.RULE_VIOLATION);
    assertThat(Files.exists(tempFile)).as("临时文件仍需清理").isFalse();
  }

  @Test
  @DisplayName("ObjectStorage.upload 抛 RuntimeException 时应包装为 FileDownloadException 并清理临时文件")
  void shouldWrapUploadFailureAndDeleteTempFile() throws IOException {
    // Given
    URI sourceUrl = URI.create("https://example.com/nature.jpg");
    String targetKey = "catalog/venue-cover/4.jpg";
    Path tempFile = Files.createTempFile("cover-upload-fail-", ".jpg");
    Files.writeString(tempFile, "fake-bytes");
    long fileSize = Files.size(tempFile);

    when(fileDownloadPort.download(sourceUrl))
        .thenReturn(FileDownloadResult.of(tempFile, fileSize));
    doThrow(new RuntimeException("MinIO unreachable"))
        .when(objectStorage)
        .upload(any(), any(), any(), any(ObjectMetadata.class));

    // When
    FileDownloadException ex =
        catchThrowableOfType(
            () -> adapter.downloadAndStore(sourceUrl, targetKey), FileDownloadException.class);

    // Then
    assertThat(ex).isNotNull();
    assertThat(ex.getMessage()).contains("上传封面到对象存储失败");
    assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
    assertThat(ex.getErrorTraits()).contains(StandardErrorTrait.DEP_UNAVAILABLE);
    assertThat(Files.exists(tempFile)).as("即使上传失败也需清理临时文件").isFalse();
  }
}
