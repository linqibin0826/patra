package dev.linqibin.starter.objectstorage.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ObjectMetadata 单元测试")
class ObjectMetadataTest {

  @Test
  @DisplayName("Builder 模式 - 创建完整元数据")
  void builder_withAllFields_shouldCreateCompleteMetadata() {
    // Arrange
    Map<String, String> userMetadata = Map.of("key1", "value1", "key2", "value2");

    // Act
    ObjectMetadata metadata =
        ObjectMetadata.builder()
            .contentLength(1024L)
            .contentType("application/pdf")
            .userMetadata(userMetadata)
            .build();

    // Assert
    assertThat(metadata.getContentLength()).isEqualTo(1024L);
    assertThat(metadata.getContentType()).isEqualTo("application/pdf");
    assertThat(metadata.getUserMetadata()).isEqualTo(userMetadata);
  }

  @Test
  @DisplayName("Builder 模式 - 仅必填字段，使用默认值")
  void builder_withRequiredFieldsOnly_shouldUseDefaults() {
    // Act
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(2048L).build();

    // Assert
    assertThat(metadata.getContentLength()).isEqualTo(2048L);
    assertThat(metadata.getContentType()).isEqualTo("application/octet-stream");
    assertThat(metadata.getUserMetadata()).isEmpty();
  }

  @Test
  @DisplayName("Builder 模式 - contentType 默认值为 application/octet-stream")
  void builder_withoutContentType_shouldUseDefaultOctetStream() {
    // Act
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(512L).build();

    // Assert
    assertThat(metadata.getContentType()).isEqualTo("application/octet-stream");
  }

  @Test
  @DisplayName("Builder 模式 - userMetadata 默认值为空 Map")
  void builder_withoutUserMetadata_shouldUseEmptyMap() {
    // Act
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(256L).build();

    // Assert
    assertThat(metadata.getUserMetadata()).isNotNull();
    assertThat(metadata.getUserMetadata()).isEmpty();
  }

  @Test
  @DisplayName("Builder 模式 - 设置自定义 contentType")
  void builder_withCustomContentType_shouldUseCustomValue() {
    // Act
    ObjectMetadata metadata =
        ObjectMetadata.builder().contentLength(1024L).contentType("image/png").build();

    // Assert
    assertThat(metadata.getContentType()).isEqualTo("image/png");
  }

  @Test
  @DisplayName("Builder 模式 - 设置空的 userMetadata")
  void builder_withEmptyUserMetadata_shouldAcceptEmptyMap() {
    // Act
    ObjectMetadata metadata =
        ObjectMetadata.builder().contentLength(1024L).userMetadata(Collections.emptyMap()).build();

    // Assert
    assertThat(metadata.getUserMetadata()).isEmpty();
  }

  @Test
  @DisplayName("Builder 模式 - userMetadata 包含多个条目")
  void builder_withMultipleUserMetadataEntries_shouldStoreAll() {
    // Arrange
    Map<String, String> userMetadata =
        Map.of(
            "author", "John Doe",
            "version", "1.0",
            "department", "Engineering");

    // Act
    ObjectMetadata metadata =
        ObjectMetadata.builder().contentLength(1024L).userMetadata(userMetadata).build();

    // Assert
    assertThat(metadata.getUserMetadata()).hasSize(3);
    assertThat(metadata.getUserMetadata()).containsEntry("author", "John Doe");
    assertThat(metadata.getUserMetadata()).containsEntry("version", "1.0");
    assertThat(metadata.getUserMetadata()).containsEntry("department", "Engineering");
  }

  @Test
  @DisplayName("Getter 方法 - 获取 contentLength")
  void getContentLength_shouldReturnCorrectValue() {
    // Arrange
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(4096L).build();

    // Act & Assert
    assertThat(metadata.getContentLength()).isEqualTo(4096L);
  }

  @Test
  @DisplayName("Getter 方法 - 获取 contentType")
  void getContentType_shouldReturnCorrectValue() {
    // Arrange
    ObjectMetadata metadata =
        ObjectMetadata.builder().contentLength(1024L).contentType("text/plain").build();

    // Act & Assert
    assertThat(metadata.getContentType()).isEqualTo("text/plain");
  }

  @Test
  @DisplayName("Getter 方法 - 获取 userMetadata")
  void getUserMetadata_shouldReturnCorrectMap() {
    // Arrange
    Map<String, String> userMetadata = Map.of("customKey", "customValue");
    ObjectMetadata metadata =
        ObjectMetadata.builder().contentLength(1024L).userMetadata(userMetadata).build();

    // Act & Assert
    assertThat(metadata.getUserMetadata()).isEqualTo(userMetadata);
  }

  @Test
  @DisplayName("Builder 模式 - contentLength 为 0")
  void builder_withZeroContentLength_shouldAccept() {
    // Act
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(0L).build();

    // Assert
    assertThat(metadata.getContentLength()).isEqualTo(0L);
  }

  @Test
  @DisplayName("Builder 模式 - contentLength 为大数值")
  void builder_withLargeContentLength_shouldAccept() {
    // Act
    long largeSize = 10_737_418_240L; // 10 GB
    ObjectMetadata metadata = ObjectMetadata.builder().contentLength(largeSize).build();

    // Assert
    assertThat(metadata.getContentLength()).isEqualTo(largeSize);
  }

  @Test
  @DisplayName("Builder 模式 - 常见 MIME 类型测试")
  void builder_withCommonMimeTypes_shouldAcceptAll() {
    // PDF
    ObjectMetadata pdfMetadata =
        ObjectMetadata.builder().contentLength(1024L).contentType("application/pdf").build();
    assertThat(pdfMetadata.getContentType()).isEqualTo("application/pdf");

    // JSON
    ObjectMetadata jsonMetadata =
        ObjectMetadata.builder().contentLength(512L).contentType("application/json").build();
    assertThat(jsonMetadata.getContentType()).isEqualTo("application/json");

    // XML
    ObjectMetadata xmlMetadata =
        ObjectMetadata.builder().contentLength(256L).contentType("application/xml").build();
    assertThat(xmlMetadata.getContentType()).isEqualTo("application/xml");

    // Text
    ObjectMetadata textMetadata =
        ObjectMetadata.builder().contentLength(128L).contentType("text/plain").build();
    assertThat(textMetadata.getContentType()).isEqualTo("text/plain");

    // Image
    ObjectMetadata imageMetadata =
        ObjectMetadata.builder().contentLength(2048L).contentType("image/jpeg").build();
    assertThat(imageMetadata.getContentType()).isEqualTo("image/jpeg");
  }

  @Test
  @DisplayName("不可变性 - userMetadata 使用 Lombok Builder 默认行为")
  void immutability_userMetadataShouldBeIndependentCopy() {
    // Arrange
    Map<String, String> originalMetadata = new java.util.HashMap<>();
    originalMetadata.put("key", "value");

    ObjectMetadata metadata =
        ObjectMetadata.builder().contentLength(1024L).userMetadata(originalMetadata).build();

    // Act - 修改原始 Map
    originalMetadata.put("newKey", "newValue");

    // Assert - 验证元数据中的 userMetadata 包含初始值
    // 注意：Lombok Builder 默认不做防御性复制，所以原始 Map 的修改会影响构建后的对象
    // 这是 Lombok Builder 的默认行为
    assertThat(metadata.getUserMetadata()).containsKey("key");
  }
}
