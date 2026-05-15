package com.patra.starter.objectstorage.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ObjectInfo 单元测试")
class ObjectInfoTest {

  @Test
  @DisplayName("构建 ObjectInfo - 所有字段应正确设置")
  void build_shouldSetAllFields() {
    // Arrange
    String bucketName = "test-bucket";
    String objectKey = "path/to/file.xml";
    long contentLength = 1024L;
    String contentType = "application/xml";
    String etag = "abc123def456";
    Instant lastModified = Instant.now();

    // Act
    ObjectInfo info =
        ObjectInfo.builder()
            .bucketName(bucketName)
            .objectKey(objectKey)
            .contentLength(contentLength)
            .contentType(contentType)
            .etag(etag)
            .lastModified(lastModified)
            .build();

    // Assert
    assertThat(info.getBucketName()).isEqualTo(bucketName);
    assertThat(info.getObjectKey()).isEqualTo(objectKey);
    assertThat(info.getContentLength()).isEqualTo(contentLength);
    assertThat(info.getContentType()).isEqualTo(contentType);
    assertThat(info.getEtag()).isEqualTo(etag);
    assertThat(info.getLastModified()).isEqualTo(lastModified);
  }

  @Test
  @DisplayName("构建 ObjectInfo - 可选字段可以为 null")
  void build_optionalFieldsCanBeNull() {
    // Arrange & Act
    ObjectInfo info =
        ObjectInfo.builder().bucketName("bucket").objectKey("key").contentLength(100L).build();

    // Assert
    assertThat(info.getBucketName()).isEqualTo("bucket");
    assertThat(info.getObjectKey()).isEqualTo("key");
    assertThat(info.getContentLength()).isEqualTo(100L);
    assertThat(info.getContentType()).isNull();
    assertThat(info.getEtag()).isNull();
    assertThat(info.getLastModified()).isNull();
  }
}
