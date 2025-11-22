package com.patra.objectstorage.domain.model.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// StorageKey 值对象单元测试。
/// 
/// 测试策略：
/// 
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///   - 使用 @Nested 分组组织测试
/// 
/// 测试范围：
/// 
/// - ✅ record 构造函数验证（紧凑构造器）
///   - ✅ bucket 为 null/空字符串/空白字符串的异常测试
///   - ✅ objectKey 为 null/空字符串/空白字符串的异常测试
///   - ✅ fullKey() 方法测试（bucket/objectKey 组合格式）
///   - ✅ matches() 方法测试（相等性比较）
///   - ✅ record 自动生成的 equals/hashCode/toString 测试
/// 
/// @author linqibin
/// @since 0.2.0
@DisplayName("StorageKey 值对象单元测试")
class StorageKeyTest {

  // ========== 构造函数验证测试 ==========

  @Nested
  @DisplayName("构造函数验证")
  class ConstructorValidationTests {

    @Test
    @DisplayName("应该成功创建 StorageKey 当 bucket 和 objectKey 都有效")
    void shouldCreateStorageKeyWhenBothParametersAreValid() {
      // Given
      String bucket = "publication-files";
      String objectKey = "2024/01/article.pdf";

      // When
      StorageKey storageKey = new StorageKey(bucket, objectKey);

      // Then
      assertThat(storageKey).isNotNull();
      assertThat(storageKey.bucket()).isEqualTo(bucket);
      assertThat(storageKey.objectKey()).isEqualTo(objectKey);
    }

    @Test
    @DisplayName("应该抛出异常当 bucket 为 null")
    void shouldThrowExceptionWhenBucketIsNull() {
      // Given
      String bucket = null;
      String objectKey = "2024/01/article.pdf";

      // When & Then
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("存储桶不能为空");
    }

    @Test
    @DisplayName("应该抛出异常当 bucket 为空字符串")
    void shouldThrowExceptionWhenBucketIsEmpty() {
      // Given
      String bucket = "";
      String objectKey = "2024/01/article.pdf";

      // When & Then
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("存储桶不能为空");
    }

    @Test
    @DisplayName("应该抛出异常当 bucket 只包含空格")
    void shouldThrowExceptionWhenBucketIsOnlyWhitespace() {
      // Given
      String bucket = "   ";
      String objectKey = "2024/01/article.pdf";

      // When & Then
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("存储桶不能为空");
    }

    @Test
    @DisplayName("应该抛出异常当 bucket 包含制表符")
    void shouldThrowExceptionWhenBucketContainsOnlyTabs() {
      // Given
      String bucket = "\t\t";
      String objectKey = "2024/01/article.pdf";

      // When & Then
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("存储桶不能为空");
    }

    @Test
    @DisplayName("应该抛出异常当 bucket 包含换行符")
    void shouldThrowExceptionWhenBucketContainsOnlyNewlines() {
      // Given
      String bucket = "\n\n";
      String objectKey = "2024/01/article.pdf";

      // When & Then
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("存储桶不能为空");
    }

    @Test
    @DisplayName("应该抛出异常当 objectKey 为 null")
    void shouldThrowExceptionWhenObjectKeyIsNull() {
      // Given
      String bucket = "publication-files";
      String objectKey = null;

      // When & Then
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("对象键不能为空");
    }

    @Test
    @DisplayName("应该抛出异常当 objectKey 为空字符串")
    void shouldThrowExceptionWhenObjectKeyIsEmpty() {
      // Given
      String bucket = "publication-files";
      String objectKey = "";

      // When & Then
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("对象键不能为空");
    }

    @Test
    @DisplayName("应该抛出异常当 objectKey 只包含空格")
    void shouldThrowExceptionWhenObjectKeyIsOnlyWhitespace() {
      // Given
      String bucket = "publication-files";
      String objectKey = "   ";

      // When & Then
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("对象键不能为空");
    }

    @Test
    @DisplayName("应该抛出异常当 objectKey 包含制表符")
    void shouldThrowExceptionWhenObjectKeyContainsOnlyTabs() {
      // Given
      String bucket = "publication-files";
      String objectKey = "\t\t";

      // When & Then
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("对象键不能为空");
    }

    @Test
    @DisplayName("应该抛出异常当 objectKey 包含换行符")
    void shouldThrowExceptionWhenObjectKeyContainsOnlyNewlines() {
      // Given
      String bucket = "publication-files";
      String objectKey = "\n\n";

      // When & Then
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("对象键不能为空");
    }

    @Test
    @DisplayName("应该抛出异常当 bucket 和 objectKey 都为 null")
    void shouldThrowExceptionWhenBothParametersAreNull() {
      // Given
      String bucket = null;
      String objectKey = null;

      // When & Then - bucket 验证先执行
      assertThatThrownBy(() -> new StorageKey(bucket, objectKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("存储桶不能为空");
    }
  }

  // ========== fullKey() 方法测试 ==========

  @Nested
  @DisplayName("fullKey() 方法")
  class FullKeyMethodTests {

    @Test
    @DisplayName("应该返回正确的 bucket/objectKey 组合格式")
    void shouldReturnCorrectBucketSlashObjectKeyFormat() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");

      // When
      String fullKey = storageKey.fullKey();

      // Then
      assertThat(fullKey).isEqualTo("publication-files/2024/01/article.pdf");
    }

    @Test
    @DisplayName("应该处理简单的 objectKey")
    void shouldHandleSimpleObjectKey() {
      // Given
      StorageKey storageKey = new StorageKey("bucket", "file.txt");

      // When
      String fullKey = storageKey.fullKey();

      // Then
      assertThat(fullKey).isEqualTo("bucket/file.txt");
    }

    @Test
    @DisplayName("应该处理多层级路径的 objectKey")
    void shouldHandleMultiLevelPathObjectKey() {
      // Given
      StorageKey storageKey = new StorageKey("documents", "projects/2024/Q1/reports/summary.pdf");

      // When
      String fullKey = storageKey.fullKey();

      // Then
      assertThat(fullKey).isEqualTo("documents/projects/2024/Q1/reports/summary.pdf");
    }

    @Test
    @DisplayName("应该处理包含特殊字符的 bucket 和 objectKey")
    void shouldHandleSpecialCharactersInBucketAndObjectKey() {
      // Given
      StorageKey storageKey = new StorageKey("文献存储桶", "2024/文章(副本).pdf");

      // When
      String fullKey = storageKey.fullKey();

      // Then
      assertThat(fullKey).isEqualTo("文献存储桶/2024/文章(副本).pdf");
    }

    @Test
    @DisplayName("应该处理 bucket 名称包含连字符")
    void shouldHandleBucketNameWithHyphens() {
      // Given
      StorageKey storageKey = new StorageKey("my-bucket-name", "path/to/file.txt");

      // When
      String fullKey = storageKey.fullKey();

      // Then
      assertThat(fullKey).isEqualTo("my-bucket-name/path/to/file.txt");
    }

    @Test
    @DisplayName("fullKey() 应该始终返回相同的结果")
    void fullKeyShouldAlwaysReturnSameResult() {
      // Given
      StorageKey storageKey = new StorageKey("bucket", "object/key");

      // When
      String fullKey1 = storageKey.fullKey();
      String fullKey2 = storageKey.fullKey();

      // Then - 幂等性
      assertThat(fullKey1).isEqualTo(fullKey2);
    }
  }

  // ========== matches() 方法测试 ==========

  @Nested
  @DisplayName("matches() 方法")
  class MatchesMethodTests {

    @Test
    @DisplayName("应该返回 true 当 bucket 和 objectKey 都匹配")
    void shouldReturnTrueWhenBothBucketAndObjectKeyMatch() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");
      String otherBucket = "publication-files";
      String otherKey = "2024/01/article.pdf";

      // When
      boolean matches = storageKey.matches(otherBucket, otherKey);

      // Then
      assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("应该返回 false 当 bucket 不匹配")
    void shouldReturnFalseWhenBucketDoesNotMatch() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");
      String otherBucket = "other-bucket";
      String otherKey = "2024/01/article.pdf";

      // When
      boolean matches = storageKey.matches(otherBucket, otherKey);

      // Then
      assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当 objectKey 不匹配")
    void shouldReturnFalseWhenObjectKeyDoesNotMatch() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");
      String otherBucket = "publication-files";
      String otherKey = "2024/02/different.pdf";

      // When
      boolean matches = storageKey.matches(otherBucket, otherKey);

      // Then
      assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当 bucket 和 objectKey 都不匹配")
    void shouldReturnFalseWhenBothBucketAndObjectKeyDoNotMatch() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");
      String otherBucket = "other-bucket";
      String otherKey = "2024/02/different.pdf";

      // When
      boolean matches = storageKey.matches(otherBucket, otherKey);

      // Then
      assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当 otherBucket 为 null")
    void shouldReturnFalseWhenOtherBucketIsNull() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");
      String otherBucket = null;
      String otherKey = "2024/01/article.pdf";

      // When
      boolean matches = storageKey.matches(otherBucket, otherKey);

      // Then
      assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当 otherKey 为 null")
    void shouldReturnFalseWhenOtherKeyIsNull() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");
      String otherBucket = "publication-files";
      String otherKey = null;

      // When
      boolean matches = storageKey.matches(otherBucket, otherKey);

      // Then
      assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当两个参数都为 null")
    void shouldReturnFalseWhenBothParametersAreNull() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");
      String otherBucket = null;
      String otherKey = null;

      // When
      boolean matches = storageKey.matches(otherBucket, otherKey);

      // Then
      assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("应该区分大小写比较")
    void shouldBeCaseSensitive() {
      // Given
      StorageKey storageKey = new StorageKey("Publication-Files", "2024/01/Article.pdf");
      String otherBucket = "publication-files";
      String otherKey = "2024/01/article.pdf";

      // When
      boolean matches = storageKey.matches(otherBucket, otherKey);

      // Then
      assertThat(matches).isFalse();
    }
  }

  // ========== record 自动生成方法测试 ==========

  @Nested
  @DisplayName("record 自动生成的 equals/hashCode/toString")
  class RecordGeneratedMethodsTests {

    @Test
    @DisplayName("应该正确实现 equals - 相同的 bucket 和 objectKey")
    void shouldImplementEqualsCorrectlyForSameValues() {
      // Given
      StorageKey storageKey1 = new StorageKey("publication-files", "2024/01/article.pdf");
      StorageKey storageKey2 = new StorageKey("publication-files", "2024/01/article.pdf");

      // When & Then
      assertThat(storageKey1).isEqualTo(storageKey2);
      assertThat(storageKey2).isEqualTo(storageKey1); // 对称性
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同的 bucket")
    void shouldImplementEqualsCorrectlyForDifferentBucket() {
      // Given
      StorageKey storageKey1 = new StorageKey("bucket1", "2024/01/article.pdf");
      StorageKey storageKey2 = new StorageKey("bucket2", "2024/01/article.pdf");

      // When & Then
      assertThat(storageKey1).isNotEqualTo(storageKey2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同的 objectKey")
    void shouldImplementEqualsCorrectlyForDifferentObjectKey() {
      // Given
      StorageKey storageKey1 = new StorageKey("publication-files", "2024/01/article.pdf");
      StorageKey storageKey2 = new StorageKey("publication-files", "2024/02/another.pdf");

      // When & Then
      assertThat(storageKey1).isNotEqualTo(storageKey2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 自反性")
    void shouldImplementEqualsReflexivity() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");

      // When & Then
      assertThat(storageKey).isEqualTo(storageKey);
    }

    @Test
    @DisplayName("应该正确实现 equals - 传递性")
    void shouldImplementEqualsTransitivity() {
      // Given
      StorageKey storageKey1 = new StorageKey("bucket", "key");
      StorageKey storageKey2 = new StorageKey("bucket", "key");
      StorageKey storageKey3 = new StorageKey("bucket", "key");

      // When & Then - 如果 a == b 且 b == c，则 a == c
      assertThat(storageKey1).isEqualTo(storageKey2);
      assertThat(storageKey2).isEqualTo(storageKey3);
      assertThat(storageKey1).isEqualTo(storageKey3);
    }

    @Test
    @DisplayName("应该正确实现 equals - 与 null 比较")
    void shouldImplementEqualsWithNull() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");

      // When & Then
      assertThat(storageKey).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确实现 equals - 与不同类型比较")
    void shouldImplementEqualsWithDifferentType() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");
      Object other = "publication-files/2024/01/article.pdf";

      // When & Then
      assertThat(storageKey).isNotEqualTo(other);
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 相同的对象产生相同的 hashCode")
    void shouldImplementHashCodeConsistentlyForEqualObjects() {
      // Given
      StorageKey storageKey1 = new StorageKey("publication-files", "2024/01/article.pdf");
      StorageKey storageKey2 = new StorageKey("publication-files", "2024/01/article.pdf");

      // When & Then - 相等的对象必须有相同的 hashCode
      assertThat(storageKey1.hashCode()).isEqualTo(storageKey2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 多次调用返回相同值")
    void shouldImplementHashCodeConsistentlyAcrossMultipleCalls() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");

      // When
      int hashCode1 = storageKey.hashCode();
      int hashCode2 = storageKey.hashCode();

      // Then
      assertThat(hashCode1).isEqualTo(hashCode2);
    }

    @Test
    @DisplayName("应该正确实现 toString - 包含 bucket 和 objectKey")
    void shouldImplementToStringWithBucketAndObjectKey() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");

      // When
      String toString = storageKey.toString();

      // Then
      assertThat(toString)
          .contains("StorageKey")
          .contains("publication-files")
          .contains("2024/01/article.pdf");
    }

    @Test
    @DisplayName("应该正确实现 toString - 格式符合 record 规范")
    void shouldImplementToStringInRecordFormat() {
      // Given
      StorageKey storageKey = new StorageKey("bucket", "key");

      // When
      String toString = storageKey.toString();

      // Then - record 的 toString 格式: ClassName[field1=value1, field2=value2]
      assertThat(toString)
          .startsWith("StorageKey[")
          .endsWith("]")
          .contains("bucket=bucket")
          .contains("objectKey=key");
    }
  }

  // ========== 值对象不变性测试 ==========

  @Nested
  @DisplayName("值对象不变性")
  class ValueObjectImmutabilityTests {

    @Test
    @DisplayName("应该保证 bucket 不可变")
    void shouldEnsureBucketIsImmutable() {
      // Given
      String originalBucket = "publication-files";
      StorageKey storageKey = new StorageKey(originalBucket, "2024/01/article.pdf");

      // When - 尝试修改原始字符串（实际上字符串本身不可变）
      String modifiedBucket = originalBucket.toUpperCase();

      // Then - storageKey 中的 bucket 应该保持不变
      assertThat(storageKey.bucket()).isEqualTo("publication-files");
      assertThat(storageKey.bucket()).isNotEqualTo(modifiedBucket);
    }

    @Test
    @DisplayName("应该保证 objectKey 不可变")
    void shouldEnsureObjectKeyIsImmutable() {
      // Given
      String originalKey = "2024/01/article.pdf";
      StorageKey storageKey = new StorageKey("publication-files", originalKey);

      // When - 尝试修改原始字符串
      String modifiedKey = originalKey.replace("article", "document");

      // Then - storageKey 中的 objectKey 应该保持不变
      assertThat(storageKey.objectKey()).isEqualTo("2024/01/article.pdf");
      assertThat(storageKey.objectKey()).isNotEqualTo(modifiedKey);
    }

    @Test
    @DisplayName("应该保证 StorageKey 完全不可变")
    void shouldEnsureStorageKeyIsCompletelyImmutable() {
      // Given
      StorageKey storageKey = new StorageKey("publication-files", "2024/01/article.pdf");
      String originalFullKey = storageKey.fullKey();

      // When - 获取所有字段
      String bucket = storageKey.bucket();
      String objectKey = storageKey.objectKey();

      // Then - 所有字段应该保持原值
      assertThat(storageKey.bucket()).isEqualTo(bucket);
      assertThat(storageKey.objectKey()).isEqualTo(objectKey);
      assertThat(storageKey.fullKey()).isEqualTo(originalFullKey);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理极短的 bucket 名称")
    void shouldHandleVeryShortBucketName() {
      // Given
      StorageKey storageKey = new StorageKey("a", "file.txt");

      // When & Then
      assertThat(storageKey.bucket()).isEqualTo("a");
      assertThat(storageKey.fullKey()).isEqualTo("a/file.txt");
    }

    @Test
    @DisplayName("应该处理极短的 objectKey")
    void shouldHandleVeryShortObjectKey() {
      // Given
      StorageKey storageKey = new StorageKey("bucket", "f");

      // When & Then
      assertThat(storageKey.objectKey()).isEqualTo("f");
      assertThat(storageKey.fullKey()).isEqualTo("bucket/f");
    }

    @Test
    @DisplayName("应该处理极长的 bucket 名称")
    void shouldHandleVeryLongBucketName() {
      // Given
      String longBucket = "a".repeat(255);
      StorageKey storageKey = new StorageKey(longBucket, "file.txt");

      // When & Then
      assertThat(storageKey.bucket()).isEqualTo(longBucket);
      assertThat(storageKey.bucket()).hasSize(255);
    }

    @Test
    @DisplayName("应该处理极长的 objectKey")
    void shouldHandleVeryLongObjectKey() {
      // Given
      String longKey = "path/to/".repeat(50) + "file.txt";
      StorageKey storageKey = new StorageKey("bucket", longKey);

      // When & Then
      assertThat(storageKey.objectKey()).isEqualTo(longKey);
      assertThat(storageKey.objectKey().length()).isGreaterThan(400);
    }

    @Test
    @DisplayName("应该处理包含 Unicode 字符的 bucket")
    void shouldHandleUnicodeBucket() {
      // Given
      StorageKey storageKey = new StorageKey("文献-存储桶-🔬", "file.txt");

      // When & Then
      assertThat(storageKey.bucket()).isEqualTo("文献-存储桶-🔬");
      assertThat(storageKey.fullKey()).isEqualTo("文献-存储桶-🔬/file.txt");
    }

    @Test
    @DisplayName("应该处理包含 Unicode 字符的 objectKey")
    void shouldHandleUnicodeObjectKey() {
      // Given
      StorageKey storageKey = new StorageKey("bucket", "文档/2024/研究报告 📊.pdf");

      // When & Then
      assertThat(storageKey.objectKey()).isEqualTo("文档/2024/研究报告 📊.pdf");
      assertThat(storageKey.fullKey()).isEqualTo("bucket/文档/2024/研究报告 📊.pdf");
    }

    @Test
    @DisplayName("应该处理包含特殊字符的路径")
    void shouldHandleSpecialCharactersInPath() {
      // Given
      StorageKey storageKey = new StorageKey("my-bucket_123", "path/to/file (copy) [2024].pdf");

      // When & Then
      assertThat(storageKey.bucket()).isEqualTo("my-bucket_123");
      assertThat(storageKey.objectKey()).isEqualTo("path/to/file (copy) [2024].pdf");
      assertThat(storageKey.fullKey()).isEqualTo("my-bucket_123/path/to/file (copy) [2024].pdf");
    }
  }

  // ========== 真实场景集成测试 ==========

  @Nested
  @DisplayName("真实场景集成测试")
  class RealWorldScenarioTests {

    @Test
    @DisplayName("应该正确处理出版物文件存储场景")
    void shouldHandlePublicationFileStorageScenario() {
      // Given - 出版物文件存储场景
      StorageKey storageKey =
          new StorageKey("patra-publication-files", "pubmed/2024/01/PMC12345678.pdf");

      // When & Then
      assertThat(storageKey.bucket()).isEqualTo("patra-publication-files");
      assertThat(storageKey.objectKey()).isEqualTo("pubmed/2024/01/PMC12345678.pdf");
      assertThat(storageKey.fullKey())
          .isEqualTo("patra-publication-files/pubmed/2024/01/PMC12345678.pdf");
      assertThat(storageKey.matches("patra-publication-files", "pubmed/2024/01/PMC12345678.pdf"))
          .isTrue();
    }

    @Test
    @DisplayName("应该正确处理附件存储场景")
    void shouldHandleAttachmentStorageScenario() {
      // Given - 附件存储场景
      StorageKey storageKey =
          new StorageKey("user-attachments", "uploads/2024/Q1/report-final-v2.xlsx");

      // When & Then
      assertThat(storageKey.bucket()).isEqualTo("user-attachments");
      assertThat(storageKey.objectKey()).isEqualTo("uploads/2024/Q1/report-final-v2.xlsx");
      assertThat(storageKey.fullKey())
          .isEqualTo("user-attachments/uploads/2024/Q1/report-final-v2.xlsx");
    }

    @Test
    @DisplayName("应该正确处理临时文件存储场景")
    void shouldHandleTemporaryFileStorageScenario() {
      // Given - 临时文件存储场景
      StorageKey storageKey = new StorageKey("temp-uploads", "tmp/session-abc123/data.json");

      // When & Then
      assertThat(storageKey.bucket()).isEqualTo("temp-uploads");
      assertThat(storageKey.objectKey()).isEqualTo("tmp/session-abc123/data.json");
      assertThat(storageKey.fullKey()).isEqualTo("temp-uploads/tmp/session-abc123/data.json");
    }

    @Test
    @DisplayName("应该支持幂等性检查场景")
    void shouldSupportIdempotencyCheckScenario() {
      // Given - 用于幂等性检查的存储键
      StorageKey storageKey1 = new StorageKey("publication-files", "epmc/2024/01/article-001.pdf");
      StorageKey storageKey2 = new StorageKey("publication-files", "epmc/2024/01/article-001.pdf");

      // When - 检查是否为相同文件
      boolean isDuplicate = storageKey1.equals(storageKey2);
      boolean keysMatch = storageKey1.matches(storageKey2.bucket(), storageKey2.objectKey());

      // Then - 应该识别为重复文件
      assertThat(isDuplicate).isTrue();
      assertThat(keysMatch).isTrue();
      assertThat(storageKey1.fullKey()).isEqualTo(storageKey2.fullKey());
    }
  }
}
