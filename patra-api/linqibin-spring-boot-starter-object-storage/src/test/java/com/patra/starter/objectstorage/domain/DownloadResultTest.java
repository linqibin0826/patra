package com.patra.starter.objectstorage.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DownloadResult 单元测试")
class DownloadResultTest {

  @Test
  @DisplayName("构建 DownloadResult - 所有字段应正确设置")
  void build_shouldSetAllFields() {
    // Arrange
    InputStream content = new ByteArrayInputStream("test content".getBytes());
    String bucketName = "test-bucket";
    String objectKey = "path/to/file.xml";
    long contentLength = 12L;
    String contentType = "application/xml";
    String etag = "abc123";

    // Act
    DownloadResult result =
        DownloadResult.builder()
            .content(content)
            .bucketName(bucketName)
            .objectKey(objectKey)
            .contentLength(contentLength)
            .contentType(contentType)
            .etag(etag)
            .build();

    // Assert
    assertThat(result.getContent()).isSameAs(content);
    assertThat(result.getBucketName()).isEqualTo(bucketName);
    assertThat(result.getObjectKey()).isEqualTo(objectKey);
    assertThat(result.getContentLength()).isEqualTo(contentLength);
    assertThat(result.getContentType()).isEqualTo(contentType);
    assertThat(result.getEtag()).isEqualTo(etag);
  }

  @Test
  @DisplayName("关闭 DownloadResult - 应关闭底层输入流")
  void close_shouldCloseUnderlyingStream() throws IOException {
    // Arrange
    TestInputStream testStream = new TestInputStream();
    DownloadResult result = DownloadResult.builder().content(testStream).build();

    // Act
    result.close();

    // Assert
    assertThat(testStream.isClosed()).isTrue();
  }

  @Test
  @DisplayName("关闭 DownloadResult - content 为 null 时不应抛出异常")
  void close_withNullContent_shouldNotThrow() throws IOException {
    // Arrange
    DownloadResult result = DownloadResult.builder().content(null).build();

    // Act & Assert - 不应抛出异常
    result.close();
  }

  @Test
  @DisplayName("try-with-resources - 应自动关闭流")
  void tryWithResources_shouldAutoClose() throws IOException {
    // Arrange
    TestInputStream testStream = new TestInputStream();

    // Act
    try (DownloadResult result = DownloadResult.builder().content(testStream).build()) {
      // 使用 result
      assertThat(result.getContent()).isNotNull();
    }

    // Assert
    assertThat(testStream.isClosed()).isTrue();
  }

  /// 用于验证流是否被关闭的测试辅助类。
  private static class TestInputStream extends InputStream {
    private boolean closed = false;

    @Override
    public int read() {
      return -1;
    }

    @Override
    public void close() throws IOException {
      super.close();
      closed = true;
    }

    public boolean isClosed() {
      return closed;
    }
  }
}
