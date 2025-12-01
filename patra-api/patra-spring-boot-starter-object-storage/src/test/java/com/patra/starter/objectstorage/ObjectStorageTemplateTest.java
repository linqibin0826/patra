package com.patra.starter.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.patra.starter.objectstorage.domain.DownloadFailedException;
import com.patra.starter.objectstorage.domain.DownloadResult;
import com.patra.starter.objectstorage.domain.InvalidDownloadRequestException;
import com.patra.starter.objectstorage.domain.InvalidUploadRequestException;
import com.patra.starter.objectstorage.domain.ObjectInfo;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.ObjectNotFoundException;
import com.patra.starter.objectstorage.domain.UploadFailedException;
import com.patra.starter.objectstorage.domain.UploadResult;
import com.patra.starter.objectstorage.metrics.ObjectStorageMetrics;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("ObjectStorageTemplate 单元测试")
class ObjectStorageTemplateTest {

  @Mock private ObjectStorageProvider provider;
  @Mock private RetryTemplate retryTemplate;
  @Mock private ObjectStorageMetrics metrics;

  private ObjectStorageTemplate template;

  private static final String BUCKET = "test-bucket";
  private static final String KEY = "test-key";
  private static final long FILE_SIZE = 1024L;

  @BeforeEach
  void setUp() {
    template = new ObjectStorageTemplate(provider, retryTemplate, metrics);
  }

  @Test
  @DisplayName("上传成功 - 应记录成功指标")
  void uploadSuccess_shouldRecordSuccessMetrics() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    UploadResult expectedResult =
        UploadResult.builder()
            .bucketName(BUCKET)
            .objectKey(KEY)
            .fileSize(FILE_SIZE)
            .etag("etag123")
            .build();

    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenReturn(expectedResult);

    // 模拟 RetryTemplate 直接执行回调
    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act
    UploadResult result = template.upload(BUCKET, KEY, inputStream, metadata);

    // Assert
    assertThat(result).isEqualTo(expectedResult);
    verify(provider).upload(BUCKET, KEY, inputStream, metadata);
    verify(metrics)
        .recordUploadSuccess(eq(ProviderType.MINIO), eq(BUCKET), anyLong(), eq(FILE_SIZE));
    verify(metrics, never()).recordUploadFailure(any(), any(), any());
    verify(metrics, never()).recordRetry(any(), any(), anyInt());
  }

  @Test
  @DisplayName("上传失败 - 验证异常应分类为 validation 错误")
  void uploadFailure_validationException_shouldClassifyAsValidation() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    InvalidUploadRequestException exception = new InvalidUploadRequestException("Invalid request");
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isInstanceOf(InvalidUploadRequestException.class);

    verify(metrics).recordUploadFailure(ProviderType.MINIO, BUCKET, "validation");
    verify(metrics, never()).recordUploadSuccess(any(), any(), anyLong(), anyLong());
  }

  @Test
  @DisplayName("上传失败 - 异常消息包含 network 应分类为 network 错误")
  void uploadFailure_networkException_shouldClassifyAsNetwork() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    RuntimeException exception = new RuntimeException("Network connection failed");
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isInstanceOf(UploadFailedException.class)
        .hasMessageContaining("上传失败");

    verify(metrics).recordUploadFailure(ProviderType.MINIO, BUCKET, "network");
  }

  @Test
  @DisplayName("上传失败 - 异常消息包含 connection 应分类为 network 错误")
  void uploadFailure_socketTimeoutException_shouldClassifyAsNetwork() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    RuntimeException exception = new RuntimeException("Connection refused");
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isInstanceOf(UploadFailedException.class);

    verify(metrics).recordUploadFailure(ProviderType.MINIO, BUCKET, "network");
  }

  @Test
  @DisplayName("上传失败 - 其他 RuntimeException 应分类为 unknown 错误")
  void uploadFailure_socketException_shouldClassifyAsNetwork() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    RuntimeException exception = new RuntimeException("Other error");
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isInstanceOf(UploadFailedException.class);

    verify(metrics).recordUploadFailure(ProviderType.MINIO, BUCKET, "unknown");
  }

  @Test
  @DisplayName("上传失败 - 异常消息包含 timeout 应分类为 network 错误")
  void uploadFailure_messageContainsTimeout_shouldClassifyAsNetwork() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    RuntimeException exception = new RuntimeException("Request timeout occurred");
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isInstanceOf(UploadFailedException.class);

    verify(metrics).recordUploadFailure(ProviderType.MINIO, BUCKET, "network");
  }

  @Test
  @DisplayName("上传失败 - 异常消息包含 auth 应分类为 auth 错误")
  void uploadFailure_messageContainsAuth_shouldClassifyAsAuth() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    RuntimeException exception = new RuntimeException("Authentication failed");
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isInstanceOf(UploadFailedException.class);

    verify(metrics).recordUploadFailure(ProviderType.MINIO, BUCKET, "auth");
  }

  @Test
  @DisplayName("上传失败 - 异常消息包含 credential 应分类为 auth 错误")
  void uploadFailure_messageContainsCredential_shouldClassifyAsAuth() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    RuntimeException exception = new RuntimeException("Invalid credential");
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isInstanceOf(UploadFailedException.class);

    verify(metrics).recordUploadFailure(ProviderType.MINIO, BUCKET, "auth");
  }

  @Test
  @DisplayName("上传失败 - 未知异常应分类为 unknown 错误")
  void uploadFailure_unknownException_shouldClassifyAsUnknown() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    RuntimeException exception = new RuntimeException("Unexpected error");
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isInstanceOf(UploadFailedException.class);

    verify(metrics).recordUploadFailure(ProviderType.MINIO, BUCKET, "unknown");
  }

  @Test
  @DisplayName("上传失败 - 异常消息为 null 应分类为 unknown 错误")
  void uploadFailure_nullMessage_shouldClassifyAsUnknown() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    RuntimeException exception = new RuntimeException();
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isInstanceOf(UploadFailedException.class);

    verify(metrics).recordUploadFailure(ProviderType.MINIO, BUCKET, "unknown");
  }

  @Test
  @DisplayName("上传重试 - 应记录重试次数")
  void uploadRetry_shouldRecordRetryCount() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    UploadResult expectedResult =
        UploadResult.builder()
            .bucketName(BUCKET)
            .objectKey(KEY)
            .fileSize(FILE_SIZE)
            .etag("etag123")
            .build();

    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenReturn(expectedResult);

    // 模拟重试 2 次后成功
    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(2);
              return callback.doWithRetry(context);
            });

    // Act
    UploadResult result = template.upload(BUCKET, KEY, inputStream, metadata);

    // Assert
    assertThat(result).isEqualTo(expectedResult);
    verify(metrics)
        .recordUploadSuccess(eq(ProviderType.MINIO), eq(BUCKET), anyLong(), eq(FILE_SIZE));
    verify(metrics).recordRetry(ProviderType.MINIO, BUCKET, 2);
  }

  @Test
  @DisplayName("上传重试 - 无重试时不记录重试指标")
  void uploadNoRetry_shouldNotRecordRetryMetrics() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    UploadResult expectedResult =
        UploadResult.builder()
            .bucketName(BUCKET)
            .objectKey(KEY)
            .fileSize(FILE_SIZE)
            .etag("etag123")
            .build();

    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenReturn(expectedResult);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act
    template.upload(BUCKET, KEY, inputStream, metadata);

    // Assert
    verify(metrics, never()).recordRetry(any(), any(), anyInt());
  }

  @Test
  @DisplayName("上传失败后重试 - 应同时记录重试和失败指标")
  void uploadFailureAfterRetry_shouldRecordBothRetryAndFailureMetrics() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    RuntimeException exception = new RuntimeException("Network error");
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    // 模拟重试 3 次后仍失败
    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(3);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isInstanceOf(UploadFailedException.class);

    verify(metrics).recordUploadFailure(ProviderType.MINIO, BUCKET, "network");
    verify(metrics).recordRetry(ProviderType.MINIO, BUCKET, 3);
  }

  @Test
  @DisplayName("上传失败 - UploadFailedException 应直接抛出不包装")
  void uploadFailure_uploadFailedException_shouldThrowDirectly() throws Exception {
    // Arrange
    InputStream inputStream = new ByteArrayInputStream("test".getBytes());
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(FILE_SIZE).build();

    UploadFailedException exception = new UploadFailedException("Upload failed", new IOException());
    when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
    when(provider.upload(BUCKET, KEY, inputStream, metadata)).thenThrow(exception);

    when(retryTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              RetryCallback<UploadResult, Exception> callback = invocation.getArgument(0);
              RetryContext context = mock(RetryContext.class);
              when(context.getRetryCount()).thenReturn(0);
              return callback.doWithRetry(context);
            });

    // Act & Assert
    assertThatThrownBy(() -> template.upload(BUCKET, KEY, inputStream, metadata))
        .isSameAs(exception);

    verify(metrics).recordUploadFailure(eq(ProviderType.MINIO), eq(BUCKET), anyString());
  }

  // ==================== download() 方法测试 ====================

  @Nested
  @DisplayName("download() 方法测试")
  class DownloadTest {

    @Test
    @DisplayName("下载成功 - 应记录成功指标")
    void downloadSuccess_shouldRecordSuccessMetrics() throws Exception {
      // Arrange
      InputStream mockContent = new ByteArrayInputStream("test content".getBytes());
      DownloadResult expectedResult =
          DownloadResult.builder()
              .content(mockContent)
              .bucketName(BUCKET)
              .objectKey(KEY)
              .contentLength(FILE_SIZE)
              .contentType("application/octet-stream")
              .etag("etag123")
              .build();

      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.download(BUCKET, KEY)).thenReturn(expectedResult);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<DownloadResult, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act
      DownloadResult result = template.download(BUCKET, KEY);

      // Assert
      assertThat(result).isEqualTo(expectedResult);
      verify(provider).download(BUCKET, KEY);
      verify(metrics)
          .recordDownloadSuccess(eq(ProviderType.MINIO), eq(BUCKET), anyLong(), eq(FILE_SIZE));
      verify(metrics, never()).recordDownloadFailure(any(), any(), any());
    }

    @Test
    @DisplayName("下载失败 - ObjectNotFoundException 应分类为 not_found 错误")
    void downloadFailure_objectNotFound_shouldClassifyAsNotFound() throws Exception {
      // Arrange
      ObjectNotFoundException exception = new ObjectNotFoundException(BUCKET, KEY);
      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.download(BUCKET, KEY)).thenThrow(exception);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<DownloadResult, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act & Assert
      assertThatThrownBy(() -> template.download(BUCKET, KEY))
          .isInstanceOf(ObjectNotFoundException.class);

      verify(metrics).recordDownloadFailure(ProviderType.MINIO, BUCKET, "not_found");
    }

    @Test
    @DisplayName("下载失败 - InvalidDownloadRequestException 应分类为 validation 错误")
    void downloadFailure_invalidRequest_shouldClassifyAsValidation() throws Exception {
      // Arrange
      InvalidDownloadRequestException exception =
          new InvalidDownloadRequestException("Invalid bucket name");
      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.download(BUCKET, KEY)).thenThrow(exception);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<DownloadResult, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act & Assert
      assertThatThrownBy(() -> template.download(BUCKET, KEY))
          .isInstanceOf(InvalidDownloadRequestException.class);

      verify(metrics).recordDownloadFailure(ProviderType.MINIO, BUCKET, "validation");
    }

    @Test
    @DisplayName("下载失败 - 网络错误应分类为 network 并包装为 DownloadFailedException")
    void downloadFailure_networkError_shouldClassifyAsNetworkAndWrap() throws Exception {
      // Arrange
      RuntimeException exception = new RuntimeException("Network connection failed");
      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.download(BUCKET, KEY)).thenThrow(exception);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<DownloadResult, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act & Assert
      assertThatThrownBy(() -> template.download(BUCKET, KEY))
          .isInstanceOf(DownloadFailedException.class)
          .hasMessageContaining("下载失败");

      verify(metrics).recordDownloadFailure(ProviderType.MINIO, BUCKET, "network");
    }

    @Test
    @DisplayName("下载重试 - 应记录重试次数")
    void downloadRetry_shouldRecordRetryCount() throws Exception {
      // Arrange
      InputStream mockContent = new ByteArrayInputStream("test content".getBytes());
      DownloadResult expectedResult =
          DownloadResult.builder()
              .content(mockContent)
              .bucketName(BUCKET)
              .objectKey(KEY)
              .contentLength(FILE_SIZE)
              .build();

      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.download(BUCKET, KEY)).thenReturn(expectedResult);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<DownloadResult, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(2);
                return callback.doWithRetry(context);
              });

      // Act
      template.download(BUCKET, KEY);

      // Assert
      verify(metrics).recordRetry(ProviderType.MINIO, BUCKET, 2);
    }

    @Test
    @DisplayName("下载失败 - DownloadFailedException 应直接抛出不包装")
    void downloadFailure_downloadFailedException_shouldThrowDirectly() throws Exception {
      // Arrange
      DownloadFailedException exception =
          new DownloadFailedException("Download failed", new IOException());
      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.download(BUCKET, KEY)).thenThrow(exception);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<DownloadResult, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act & Assert
      assertThatThrownBy(() -> template.download(BUCKET, KEY)).isSameAs(exception);
    }
  }

  // ==================== downloadToFile() 方法测试 ====================

  @Nested
  @DisplayName("downloadToFile() 方法测试")
  class DownloadToFileTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("下载到文件成功 - 应记录成功指标")
    void downloadToFileSuccess_shouldRecordSuccessMetrics() throws Exception {
      // Arrange
      Path targetPath = tempDir.resolve("test-file.txt");
      Files.writeString(targetPath, "test content"); // 创建文件以便获取大小
      long expectedFileSize = Files.size(targetPath);

      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.downloadToFile(BUCKET, KEY, targetPath)).thenReturn(targetPath);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<Path, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act
      Path result = template.downloadToFile(BUCKET, KEY, targetPath);

      // Assert
      assertThat(result).isEqualTo(targetPath);
      verify(provider).downloadToFile(BUCKET, KEY, targetPath);
      verify(metrics)
          .recordDownloadSuccess(
              eq(ProviderType.MINIO), eq(BUCKET), anyLong(), eq(expectedFileSize));
    }

    @Test
    @DisplayName("下载到文件失败 - ObjectNotFoundException 应分类为 not_found 错误")
    void downloadToFileFailure_objectNotFound_shouldClassifyAsNotFound() throws Exception {
      // Arrange
      Path targetPath = tempDir.resolve("test-file.txt");
      ObjectNotFoundException exception = new ObjectNotFoundException(BUCKET, KEY);
      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.downloadToFile(BUCKET, KEY, targetPath)).thenThrow(exception);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<Path, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act & Assert
      assertThatThrownBy(() -> template.downloadToFile(BUCKET, KEY, targetPath))
          .isInstanceOf(ObjectNotFoundException.class);

      verify(metrics).recordDownloadFailure(ProviderType.MINIO, BUCKET, "not_found");
    }

    @Test
    @DisplayName("下载到文件失败 - 未知异常应包装为 DownloadFailedException")
    void downloadToFileFailure_unknownException_shouldWrap() throws Exception {
      // Arrange
      Path targetPath = tempDir.resolve("test-file.txt");
      RuntimeException exception = new RuntimeException("Unknown error");
      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.downloadToFile(BUCKET, KEY, targetPath)).thenThrow(exception);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<Path, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act & Assert
      assertThatThrownBy(() -> template.downloadToFile(BUCKET, KEY, targetPath))
          .isInstanceOf(DownloadFailedException.class)
          .hasMessageContaining("下载到文件失败");

      verify(metrics).recordDownloadFailure(ProviderType.MINIO, BUCKET, "unknown");
    }

    @Test
    @DisplayName("下载到文件重试 - 应记录重试次数")
    void downloadToFileRetry_shouldRecordRetryCount() throws Exception {
      // Arrange
      Path targetPath = tempDir.resolve("test-file.txt");
      Files.writeString(targetPath, "test content");

      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.downloadToFile(BUCKET, KEY, targetPath)).thenReturn(targetPath);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<Path, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(3);
                return callback.doWithRetry(context);
              });

      // Act
      template.downloadToFile(BUCKET, KEY, targetPath);

      // Assert
      verify(metrics).recordRetry(ProviderType.MINIO, BUCKET, 3);
    }
  }

  // ==================== statObject() 方法测试 ====================

  @Nested
  @DisplayName("statObject() 方法测试")
  class StatObjectTest {

    @Test
    @DisplayName("获取对象信息成功 - 应返回 ObjectInfo")
    void statObjectSuccess_shouldReturnObjectInfo() throws Exception {
      // Arrange
      ObjectInfo expectedInfo =
          ObjectInfo.builder()
              .bucketName(BUCKET)
              .objectKey(KEY)
              .contentLength(FILE_SIZE)
              .contentType("application/octet-stream")
              .etag("etag123")
              .lastModified(Instant.now())
              .build();

      when(provider.statObject(BUCKET, KEY)).thenReturn(Optional.of(expectedInfo));

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<Optional<ObjectInfo>, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act
      Optional<ObjectInfo> result = template.statObject(BUCKET, KEY);

      // Assert
      assertThat(result).isPresent().contains(expectedInfo);
      verify(provider).statObject(BUCKET, KEY);
    }

    @Test
    @DisplayName("获取对象信息 - 对象不存在应返回空 Optional")
    void statObject_objectNotExists_shouldReturnEmpty() throws Exception {
      // Arrange
      when(provider.statObject(BUCKET, KEY)).thenReturn(Optional.empty());

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<Optional<ObjectInfo>, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act
      Optional<ObjectInfo> result = template.statObject(BUCKET, KEY);

      // Assert
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("获取对象信息失败 - 应记录失败指标并抛出异常")
    void statObjectFailure_shouldRecordFailureMetrics() throws Exception {
      // Arrange
      RuntimeException exception = new RuntimeException("Network error");
      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.statObject(BUCKET, KEY)).thenThrow(exception);

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<Optional<ObjectInfo>, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(0);
                return callback.doWithRetry(context);
              });

      // Act & Assert
      assertThatThrownBy(() -> template.statObject(BUCKET, KEY))
          .isInstanceOf(DownloadFailedException.class)
          .hasMessageContaining("获取对象信息失败");

      verify(metrics).recordDownloadFailure(ProviderType.MINIO, BUCKET, "network");
    }

    @Test
    @DisplayName("获取对象信息重试 - 应记录重试次数")
    void statObjectRetry_shouldRecordRetryCount() throws Exception {
      // Arrange
      when(provider.getProviderType()).thenReturn(ProviderType.MINIO);
      when(provider.statObject(BUCKET, KEY)).thenReturn(Optional.empty());

      when(retryTemplate.execute(any()))
          .thenAnswer(
              invocation -> {
                RetryCallback<Optional<ObjectInfo>, Exception> callback = invocation.getArgument(0);
                RetryContext context = mock(RetryContext.class);
                when(context.getRetryCount()).thenReturn(2);
                return callback.doWithRetry(context);
              });

      // Act
      template.statObject(BUCKET, KEY);

      // Assert
      verify(metrics).recordRetry(ProviderType.MINIO, BUCKET, 2);
    }
  }
}
