package dev.linqibin.starter.objectstorage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linqibin.starter.objectstorage.domain.DownloadResult;
import dev.linqibin.starter.objectstorage.domain.InvalidDownloadRequestException;
import dev.linqibin.starter.objectstorage.domain.InvalidUploadRequestException;
import dev.linqibin.starter.objectstorage.domain.ObjectInfo;
import dev.linqibin.starter.objectstorage.domain.ObjectMetadata;
import dev.linqibin.starter.objectstorage.domain.UploadResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// AbstractObjectStorageProvider 单元测试。
///
/// **测试策略**：
///
/// 创建具体子类 TestableObjectStorageProvider 来测试抽象基类的
/// protected 验证方法，验证所有参数验证规则的边界条件。
///
/// **测试范围**：
///
/// - 存储桶名称验证（长度、字符、格式）
/// - 对象键验证（长度、格式）
/// - 文件大小验证
/// - 上传参数组合验证
/// - 下载参数组合验证
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("AbstractObjectStorageProvider 单元测试")
@Timeout(2)
class AbstractObjectStorageProviderTest {

  private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
  private TestableObjectStorageProvider provider;

  @BeforeEach
  void setUp() {
    provider = new TestableObjectStorageProvider(MAX_FILE_SIZE);
  }

  @Nested
  @DisplayName("上传参数验证 - validateUploadArguments()")
  class UploadValidationTests {

    @Nested
    @DisplayName("存储桶名称验证")
    class BucketNameUploadTests {

      @Test
      @DisplayName("存储桶名称为 null 应抛出异常")
      void validateUpload_nullBucket_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(() -> provider.testValidateUploadArguments(null, "key", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("存储桶名称不能为空");
      }

      @Test
      @DisplayName("存储桶名称为空字符串应抛出异常")
      void validateUpload_emptyBucket_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(() -> provider.testValidateUploadArguments("", "key", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("存储桶名称不能为空");
      }

      @Test
      @DisplayName("存储桶名称过短（<3）应抛出异常")
      void validateUpload_bucketTooShort_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(() -> provider.testValidateUploadArguments("ab", "key", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("长度必须在 3 到 63 个字符之间");
      }

      @Test
      @DisplayName("存储桶名称过长（>63）应抛出异常")
      void validateUpload_bucketTooLong_shouldThrow() {
        // Given
        String longBucket = "a".repeat(64);
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(
                () -> provider.testValidateUploadArguments(longBucket, "key", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("长度必须在 3 到 63 个字符之间");
      }

      @ParameterizedTest
      @DisplayName("存储桶名称包含无效字符应抛出异常")
      @ValueSource(strings = {"Bucket", "bucket_name", "bucket@name", "bucket name"})
      void validateUpload_bucketInvalidChars_shouldThrow(String invalidBucket) {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(
                () -> provider.testValidateUploadArguments(invalidBucket, "key", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("无效");
      }

      @Test
      @DisplayName("存储桶名称包含连续点应抛出异常")
      void validateUpload_bucketWithConsecutiveDots_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(
                () -> provider.testValidateUploadArguments("bucket..name", "key", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("连续的点");
      }

      @ParameterizedTest
      @DisplayName("有效的存储桶名称应通过验证")
      @ValueSource(strings = {"abc", "my-bucket", "bucket.name", "bucket-name.v2", "a1b2c3"})
      void validateUpload_validBucket_shouldPass(String validBucket) {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatCode(() -> provider.testValidateUploadArguments(validBucket, "key", is, metadata))
            .doesNotThrowAnyException();
      }
    }

    @Nested
    @DisplayName("对象键验证")
    class ObjectKeyUploadTests {

      @Test
      @DisplayName("对象键为 null 应抛出异常")
      void validateUpload_nullKey_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(() -> provider.testValidateUploadArguments("bucket", null, is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("对象键不能为空");
      }

      @Test
      @DisplayName("对象键为空字符串应抛出异常")
      void validateUpload_emptyKey_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(() -> provider.testValidateUploadArguments("bucket", "", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("对象键不能为空");
      }

      @Test
      @DisplayName("对象键过长（>1024）应抛出异常")
      void validateUpload_keyTooLong_shouldThrow() {
        // Given
        String longKey = "a".repeat(1025);
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(
                () -> provider.testValidateUploadArguments("bucket", longKey, is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("对象键长度超过最大值");
      }

      @Test
      @DisplayName("对象键以斜杠开头应抛出异常")
      void validateUpload_keyStartsWithSlash_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(
                () -> provider.testValidateUploadArguments("bucket", "/path/to/file", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("不能以斜杠开头");
      }

      @Test
      @DisplayName("对象键包含连续斜杠应抛出异常")
      void validateUpload_keyWithConsecutiveSlashes_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(
                () -> provider.testValidateUploadArguments("bucket", "path//to/file", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("连续的斜杠");
      }

      @ParameterizedTest
      @DisplayName("有效的对象键应通过验证")
      @ValueSource(strings = {"file.txt", "path/to/file.txt", "2025/01/01/data.json"})
      void validateUpload_validKey_shouldPass(String validKey) {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatCode(() -> provider.testValidateUploadArguments("bucket", validKey, is, metadata))
            .doesNotThrowAnyException();
      }
    }

    @Nested
    @DisplayName("输入流和元数据验证")
    class StreamAndMetadataTests {

      @Test
      @DisplayName("输入流为 null 应抛出异常")
      void validateUpload_nullInputStream_shouldThrow() {
        // Given
        ObjectMetadata metadata = createMetadata(4);

        // When & Then
        assertThatThrownBy(
                () -> provider.testValidateUploadArguments("bucket", "key", null, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("输入流不能为 null");
      }

      @Test
      @DisplayName("元数据为 null 应抛出异常")
      void validateUpload_nullMetadata_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");

        // When & Then
        assertThatThrownBy(() -> provider.testValidateUploadArguments("bucket", "key", is, null))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("对象元数据是必需的");
      }

      @Test
      @DisplayName("内容长度为 0 应抛出异常")
      void validateUpload_zeroContentLength_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(0);

        // When & Then
        assertThatThrownBy(
                () -> provider.testValidateUploadArguments("bucket", "key", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("内容长度必须大于 0");
      }

      @Test
      @DisplayName("内容长度为负数应抛出异常")
      void validateUpload_negativeContentLength_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(-1);

        // When & Then
        assertThatThrownBy(
                () -> provider.testValidateUploadArguments("bucket", "key", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("内容长度必须大于 0");
      }

      @Test
      @DisplayName("文件大小超过限制应抛出异常")
      void validateUpload_fileSizeExceedsLimit_shouldThrow() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(MAX_FILE_SIZE + 1);

        // When & Then
        assertThatThrownBy(
                () -> provider.testValidateUploadArguments("bucket", "key", is, metadata))
            .isInstanceOf(InvalidUploadRequestException.class)
            .hasMessageContaining("文件大小")
            .hasMessageContaining("超过了最大允许大小");
      }

      @Test
      @DisplayName("文件大小等于限制应通过验证")
      void validateUpload_fileSizeAtLimit_shouldPass() {
        // Given
        InputStream is = createInputStream("test");
        ObjectMetadata metadata = createMetadata(MAX_FILE_SIZE);

        // When & Then
        assertThatCode(() -> provider.testValidateUploadArguments("bucket", "key", is, metadata))
            .doesNotThrowAnyException();
      }
    }
  }

  @Nested
  @DisplayName("下载参数验证 - validateDownloadArguments()")
  class DownloadValidationTests {

    @Nested
    @DisplayName("存储桶名称验证")
    class BucketNameDownloadTests {

      @Test
      @DisplayName("存储桶名称为 null 应抛出异常")
      void validateDownload_nullBucket_shouldThrow() {
        // When & Then
        assertThatThrownBy(() -> provider.testValidateDownloadArguments(null, "key"))
            .isInstanceOf(InvalidDownloadRequestException.class)
            .hasMessageContaining("存储桶名称不能为空");
      }

      @Test
      @DisplayName("存储桶名称为空字符串应抛出异常")
      void validateDownload_emptyBucket_shouldThrow() {
        // When & Then
        assertThatThrownBy(() -> provider.testValidateDownloadArguments("", "key"))
            .isInstanceOf(InvalidDownloadRequestException.class)
            .hasMessageContaining("存储桶名称不能为空");
      }

      @Test
      @DisplayName("存储桶名称过短应抛出异常")
      void validateDownload_bucketTooShort_shouldThrow() {
        // When & Then
        assertThatThrownBy(() -> provider.testValidateDownloadArguments("ab", "key"))
            .isInstanceOf(InvalidDownloadRequestException.class)
            .hasMessageContaining("长度必须在 3 到 63 个字符之间");
      }

      @Test
      @DisplayName("存储桶名称包含无效字符应抛出异常")
      void validateDownload_bucketInvalidChars_shouldThrow() {
        // When & Then
        assertThatThrownBy(() -> provider.testValidateDownloadArguments("Invalid_Bucket", "key"))
            .isInstanceOf(InvalidDownloadRequestException.class)
            .hasMessageContaining("无效");
      }
    }

    @Nested
    @DisplayName("对象键验证")
    class ObjectKeyDownloadTests {

      @Test
      @DisplayName("对象键为 null 应抛出异常")
      void validateDownload_nullKey_shouldThrow() {
        // When & Then
        assertThatThrownBy(() -> provider.testValidateDownloadArguments("bucket", null))
            .isInstanceOf(InvalidDownloadRequestException.class)
            .hasMessageContaining("对象键不能为空");
      }

      @Test
      @DisplayName("对象键为空字符串应抛出异常")
      void validateDownload_emptyKey_shouldThrow() {
        // When & Then
        assertThatThrownBy(() -> provider.testValidateDownloadArguments("bucket", ""))
            .isInstanceOf(InvalidDownloadRequestException.class)
            .hasMessageContaining("对象键不能为空");
      }

      @Test
      @DisplayName("对象键过长应抛出异常")
      void validateDownload_keyTooLong_shouldThrow() {
        // Given
        String longKey = "a".repeat(1025);

        // When & Then
        assertThatThrownBy(() -> provider.testValidateDownloadArguments("bucket", longKey))
            .isInstanceOf(InvalidDownloadRequestException.class)
            .hasMessageContaining("对象键长度超过最大值");
      }

      @Test
      @DisplayName("对象键以斜杠开头应抛出异常")
      void validateDownload_keyStartsWithSlash_shouldThrow() {
        // When & Then
        assertThatThrownBy(() -> provider.testValidateDownloadArguments("bucket", "/path/to/file"))
            .isInstanceOf(InvalidDownloadRequestException.class)
            .hasMessageContaining("不能以斜杠开头");
      }

      @Test
      @DisplayName("下载时对象键包含连续斜杠应通过（兼容历史数据）")
      void validateDownload_keyWithConsecutiveSlashes_shouldPass() {
        // 下载时不检查连续斜杠，因为可能需要下载历史遗留数据
        // When & Then
        assertThatCode(() -> provider.testValidateDownloadArguments("bucket", "path//to/file"))
            .doesNotThrowAnyException();
      }

      @ParameterizedTest
      @DisplayName("有效的对象键应通过验证")
      @ValueSource(strings = {"file.txt", "path/to/file.txt", "2025/01/01/data.json"})
      void validateDownload_validKey_shouldPass(String validKey) {
        // When & Then
        assertThatCode(() -> provider.testValidateDownloadArguments("bucket", validKey))
            .doesNotThrowAnyException();
      }
    }
  }

  // ==================== 辅助方法 ====================

  private InputStream createInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  private ObjectMetadata createMetadata(long contentLength) {
    return ObjectMetadata.builder().contentLength(contentLength).contentType("text/plain").build();
  }

  // ==================== 可测试的子类 ====================

  /// 可测试的 AbstractObjectStorageProvider 子类。
  ///
  /// 将 protected 验证方法暴露为 public 方法以便测试。
  private static class TestableObjectStorageProvider extends AbstractObjectStorageProvider {

    TestableObjectStorageProvider(long maxFileSize) {
      super(maxFileSize);
    }

    /// 暴露上传参数验证方法。
    void testValidateUploadArguments(
        String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
      validateUploadArguments(bucket, key, inputStream, metadata);
    }

    /// 暴露下载参数验证方法。
    void testValidateDownloadArguments(String bucket, String key) {
      validateDownloadArguments(bucket, key);
    }

    // ========== 抽象方法的空实现 ==========

    @Override
    public ProviderType getProviderType() {
      return ProviderType.MINIO; // 测试用，返回任意类型
    }

    @Override
    public UploadResult upload(
        String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
      throw new UnsupportedOperationException("测试用子类不实现此方法");
    }

    @Override
    public DownloadResult download(String bucket, String key) {
      throw new UnsupportedOperationException("测试用子类不实现此方法");
    }

    @Override
    public Path downloadToFile(String bucket, String key, Path targetPath) {
      throw new UnsupportedOperationException("测试用子类不实现此方法");
    }

    @Override
    public Optional<ObjectInfo> statObject(String bucket, String key) {
      throw new UnsupportedOperationException("测试用子类不实现此方法");
    }
  }
}
