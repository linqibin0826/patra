package com.patra.catalog.infra.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.catalog.domain.port.source.FileDownloadResult;
import com.patra.starter.objectstorage.ObjectStorageOperations;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// VenueCoverImageDownloadAdapter 单元测试。
///
/// 验证下载 + 上传 + 临时文件清理的完整生命周期，以及各种异常路径的语义特征。
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
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
}
