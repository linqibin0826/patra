package com.patra.storage.domain.model.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * FileChecksum 值对象单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 *   <li>使用 @Nested 分组组织测试
 * </ul>
 *
 * <p>测试范围：
 *
 * <ul>
 *   <li>✅ record 构造函数验证（紧凑构造器）
 *   <li>✅ 至少一个哈希值规则（MD5 或 SHA-256）
 *   <li>✅ 标准化逻辑（trim + toLowerCase）
 *   <li>✅ null 处理（保持 null 当一个哈希有值时）
 *   <li>✅ record 自动生成的 equals/hashCode/toString 测试
 *   <li>✅ 边界条件（哈希长度、特殊字符、混合大小写）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("FileChecksum 值对象单元测试")
class FileChecksumTest {

  // ========== 构造函数验证测试 - 至少一个哈希值规则 ==========

  @Nested
  @DisplayName("构造函数验证 - 至少一个哈希值规则")
  class ConstructorValidationTests {

    @Test
    @DisplayName("应该抛出异常当两个哈希都为 null")
    void shouldThrowExceptionWhenBothHashesAreNull() {
      // Given
      String md5Hash = null;
      String sha256Hash = null;

      // When & Then
      assertThatThrownBy(() -> new FileChecksum(md5Hash, sha256Hash))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须提供 MD5 或 SHA-256 哈希值");
    }

    @Test
    @DisplayName("应该抛出异常当两个哈希都为空字符串")
    void shouldThrowExceptionWhenBothHashesAreEmpty() {
      // Given
      String md5Hash = "";
      String sha256Hash = "";

      // When & Then
      assertThatThrownBy(() -> new FileChecksum(md5Hash, sha256Hash))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须提供 MD5 或 SHA-256 哈希值");
    }

    @Test
    @DisplayName("应该抛出异常当两个哈希都为空白字符串（只含空格）")
    void shouldThrowExceptionWhenBothHashesAreOnlyWhitespace() {
      // Given
      String md5Hash = "   ";
      String sha256Hash = "   ";

      // When & Then
      assertThatThrownBy(() -> new FileChecksum(md5Hash, sha256Hash))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须提供 MD5 或 SHA-256 哈希值");
    }

    @Test
    @DisplayName("应该抛出异常当两个哈希都为空白字符串（含制表符）")
    void shouldThrowExceptionWhenBothHashesContainOnlyTabs() {
      // Given
      String md5Hash = "\t\t";
      String sha256Hash = "\t\t";

      // When & Then
      assertThatThrownBy(() -> new FileChecksum(md5Hash, sha256Hash))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须提供 MD5 或 SHA-256 哈希值");
    }

    @Test
    @DisplayName("应该抛出异常当两个哈希都为空白字符串（含换行符）")
    void shouldThrowExceptionWhenBothHashesContainOnlyNewlines() {
      // Given
      String md5Hash = "\n\n";
      String sha256Hash = "\n\n";

      // When & Then
      assertThatThrownBy(() -> new FileChecksum(md5Hash, sha256Hash))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须提供 MD5 或 SHA-256 哈希值");
    }

    @Test
    @DisplayName("应该抛出异常当两个哈希都为混合空白字符串（空格+制表符+换行符）")
    void shouldThrowExceptionWhenBothHashesContainMixedWhitespace() {
      // Given
      String md5Hash = " \t\n ";
      String sha256Hash = " \n\t ";

      // When & Then
      assertThatThrownBy(() -> new FileChecksum(md5Hash, sha256Hash))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须提供 MD5 或 SHA-256 哈希值");
    }

    @Test
    @DisplayName("应该抛出异常当 MD5 为 null 且 SHA-256 为空白")
    void shouldThrowExceptionWhenMd5IsNullAndSha256IsBlank() {
      // Given
      String md5Hash = null;
      String sha256Hash = "   ";

      // When & Then
      assertThatThrownBy(() -> new FileChecksum(md5Hash, sha256Hash))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须提供 MD5 或 SHA-256 哈希值");
    }

    @Test
    @DisplayName("应该抛出异常当 MD5 为空白且 SHA-256 为 null")
    void shouldThrowExceptionWhenMd5IsBlankAndSha256IsNull() {
      // Given
      String md5Hash = "   ";
      String sha256Hash = null;

      // When & Then
      assertThatThrownBy(() -> new FileChecksum(md5Hash, sha256Hash))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("必须提供 MD5 或 SHA-256 哈希值");
    }
  }

  // ========== 合法构造测试 ==========

  @Nested
  @DisplayName("合法构造")
  class ValidConstructionTests {

    @Test
    @DisplayName("应该成功创建当只提供 MD5（SHA-256 为 null）")
    void shouldCreateSuccessfullyWhenOnlyMd5Provided() {
      // Given
      String md5Hash = "5d41402abc4b2a76b9719d911017c592";
      String sha256Hash = null;

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, sha256Hash);

      // Then
      assertThat(checksum).isNotNull();
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
      assertThat(checksum.sha256Hash()).isNull();
    }

    @Test
    @DisplayName("应该成功创建当只提供 SHA-256（MD5 为 null）")
    void shouldCreateSuccessfullyWhenOnlySha256Provided() {
      // Given
      String md5Hash = null;
      String sha256Hash =
          "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, sha256Hash);

      // Then
      assertThat(checksum).isNotNull();
      assertThat(checksum.md5Hash()).isNull();
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该成功创建当同时提供 MD5 和 SHA-256")
    void shouldCreateSuccessfullyWhenBothHashesProvided() {
      // Given
      String md5Hash = "5d41402abc4b2a76b9719d911017c592";
      String sha256Hash =
          "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, sha256Hash);

      // Then
      assertThat(checksum).isNotNull();
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }
  }

  // ========== 标准化测试 - toLowerCase ==========

  @Nested
  @DisplayName("标准化 - 大写转小写")
  class NormalizationToLowerCaseTests {

    @Test
    @DisplayName("应该将 MD5 大写字母转换为小写")
    void shouldConvertMd5UpperCaseToLowerCase() {
      // Given
      String md5Hash = "5D41402ABC4B2A76B9719D911017C592";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    @DisplayName("应该将 SHA-256 大写字母转换为小写")
    void shouldConvertSha256UpperCaseToLowerCase() {
      // Given
      String sha256Hash =
          "2C26B46B68FFC68FF99B453C1D30413413422D706483BFA0F98A5E886266E7AE";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该将 MD5 混合大小写转换为小写")
    void shouldConvertMd5MixedCaseToLowerCase() {
      // Given
      String md5Hash = "5D41402aBc4B2A76b9719D911017C592";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    @DisplayName("应该将 SHA-256 混合大小写转换为小写")
    void shouldConvertSha256MixedCaseToLowerCase() {
      // Given
      String sha256Hash =
          "2C26B46b68FfC68fF99B453C1d30413413422D706483BFA0f98A5E886266E7AE";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该保持 MD5 小写字母不变")
    void shouldKeepMd5LowerCaseUnchanged() {
      // Given
      String md5Hash = "5d41402abc4b2a76b9719d911017c592";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    @DisplayName("应该保持 SHA-256 小写字母不变")
    void shouldKeepSha256LowerCaseUnchanged() {
      // Given
      String sha256Hash =
          "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该同时转换 MD5 和 SHA-256 的大写字母")
    void shouldConvertBothHashesToLowerCase() {
      // Given
      String md5Hash = "5D41402ABC4B2A76B9719D911017C592";
      String sha256Hash =
          "2C26B46B68FFC68FF99B453C1D30413413422D706483BFA0F98A5E886266E7AE";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, sha256Hash);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }
  }

  // ========== 标准化测试 - trim ==========

  @Nested
  @DisplayName("标准化 - 自动 trim")
  class NormalizationTrimTests {

    @Test
    @DisplayName("应该 trim MD5 的前导空格")
    void shouldTrimMd5LeadingWhitespace() {
      // Given
      String md5Hash = "   5d41402abc4b2a76b9719d911017c592";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    @DisplayName("应该 trim MD5 的尾随空格")
    void shouldTrimMd5TrailingWhitespace() {
      // Given
      String md5Hash = "5d41402abc4b2a76b9719d911017c592   ";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    @DisplayName("应该 trim MD5 的前导和尾随空格")
    void shouldTrimMd5BothSidesWhitespace() {
      // Given
      String md5Hash = "   5d41402abc4b2a76b9719d911017c592   ";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    @DisplayName("应该 trim SHA-256 的前导空格")
    void shouldTrimSha256LeadingWhitespace() {
      // Given
      String sha256Hash =
          "   2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该 trim SHA-256 的尾随空格")
    void shouldTrimSha256TrailingWhitespace() {
      // Given
      String sha256Hash =
          "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae   ";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该 trim SHA-256 的前导和尾随空格")
    void shouldTrimSha256BothSidesWhitespace() {
      // Given
      String sha256Hash =
          "   2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae   ";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该 trim MD5 的制表符和换行符")
    void shouldTrimMd5TabsAndNewlines() {
      // Given
      String md5Hash = "\t\n5d41402abc4b2a76b9719d911017c592\n\t";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    @DisplayName("应该 trim SHA-256 的制表符和换行符")
    void shouldTrimSha256TabsAndNewlines() {
      // Given
      String sha256Hash =
          "\t\n2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae\n\t";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }
  }

  // ========== 标准化测试 - 组合（trim + toLowerCase）==========

  @Nested
  @DisplayName("标准化 - trim + toLowerCase 组合")
  class NormalizationCombinedTests {

    @Test
    @DisplayName("应该同时 trim 和 toLowerCase MD5")
    void shouldTrimAndLowerCaseMd5() {
      // Given
      String md5Hash = "   5D41402ABC4B2A76B9719D911017C592   ";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    @DisplayName("应该同时 trim 和 toLowerCase SHA-256")
    void shouldTrimAndLowerCaseSha256() {
      // Given
      String sha256Hash =
          "   2C26B46B68FFC68FF99B453C1D30413413422D706483BFA0F98A5E886266E7AE   ";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该同时 trim 和 toLowerCase 两个哈希值")
    void shouldTrimAndLowerCaseBothHashes() {
      // Given
      String md5Hash = "\t5D41402ABC4B2A76B9719D911017C592\n";
      String sha256Hash =
          " 2C26B46B68FFC68FF99B453C1D30413413422D706483BFA0F98A5E886266E7AE ";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, sha256Hash);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }
  }

  // ========== null 处理测试 ==========

  @Nested
  @DisplayName("null 处理")
  class NullHandlingTests {

    @Test
    @DisplayName("应该保持 MD5 为 null 当 SHA-256 有值")
    void shouldKeepMd5NullWhenSha256HasValue() {
      // Given
      String md5Hash = null;
      String sha256Hash =
          "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, sha256Hash);

      // Then
      assertThat(checksum.md5Hash()).isNull();
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该保持 SHA-256 为 null 当 MD5 有值")
    void shouldKeepSha256NullWhenMd5HasValue() {
      // Given
      String md5Hash = "5d41402abc4b2a76b9719d911017c592";
      String sha256Hash = null;

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, sha256Hash);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
      assertThat(checksum.sha256Hash()).isNull();
    }
  }

  // ========== record 自动生成方法测试 ==========

  @Nested
  @DisplayName("record 自动生成的 equals/hashCode/toString")
  class RecordGeneratedMethodsTests {

    @Test
    @DisplayName("应该正确实现 equals - 相同的哈希值（标准化后）")
    void shouldImplementEqualsCorrectlyForSameValues() {
      // Given
      FileChecksum checksum1 =
          new FileChecksum(
              "5d41402abc4b2a76b9719d911017c592",
              "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
      FileChecksum checksum2 =
          new FileChecksum(
              "5d41402abc4b2a76b9719d911017c592",
              "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");

      // When & Then
      assertThat(checksum1).isEqualTo(checksum2);
      assertThat(checksum2).isEqualTo(checksum1); // 对称性
    }

    @Test
    @DisplayName("应该正确实现 equals - 大写和小写应相等（标准化后）")
    void shouldImplementEqualsAfterNormalization() {
      // Given - 一个大写，一个小写，但标准化后应相等
      FileChecksum checksum1 = new FileChecksum("5D41402ABC4B2A76B9719D911017C592", null);
      FileChecksum checksum2 = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);

      // When & Then
      assertThat(checksum1).isEqualTo(checksum2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同的 MD5")
    void shouldImplementEqualsCorrectlyForDifferentMd5() {
      // Given
      FileChecksum checksum1 = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);
      FileChecksum checksum2 = new FileChecksum("098f6bcd4621d373cade4e832627b4f6", null);

      // When & Then
      assertThat(checksum1).isNotEqualTo(checksum2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同的 SHA-256")
    void shouldImplementEqualsCorrectlyForDifferentSha256() {
      // Given
      FileChecksum checksum1 =
          new FileChecksum(
              null, "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
      FileChecksum checksum2 =
          new FileChecksum(
              null, "fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9");

      // When & Then
      assertThat(checksum1).isNotEqualTo(checksum2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 自反性")
    void shouldImplementEqualsReflexivity() {
      // Given
      FileChecksum checksum = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);

      // When & Then
      assertThat(checksum).isEqualTo(checksum);
    }

    @Test
    @DisplayName("应该正确实现 equals - 传递性")
    void shouldImplementEqualsTransitivity() {
      // Given
      FileChecksum checksum1 = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);
      FileChecksum checksum2 = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);
      FileChecksum checksum3 = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);

      // When & Then - 如果 a == b 且 b == c，则 a == c
      assertThat(checksum1).isEqualTo(checksum2);
      assertThat(checksum2).isEqualTo(checksum3);
      assertThat(checksum1).isEqualTo(checksum3);
    }

    @Test
    @DisplayName("应该正确实现 equals - 与 null 比较")
    void shouldImplementEqualsWithNull() {
      // Given
      FileChecksum checksum = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);

      // When & Then
      assertThat(checksum).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确实现 equals - 与不同类型比较")
    void shouldImplementEqualsWithDifferentType() {
      // Given
      FileChecksum checksum = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);
      Object other = "5d41402abc4b2a76b9719d911017c592";

      // When & Then
      assertThat(checksum).isNotEqualTo(other);
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 相同的对象产生相同的 hashCode")
    void shouldImplementHashCodeConsistentlyForEqualObjects() {
      // Given
      FileChecksum checksum1 = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);
      FileChecksum checksum2 = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);

      // When & Then - 相等的对象必须有相同的 hashCode
      assertThat(checksum1.hashCode()).isEqualTo(checksum2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 标准化后产生相同的 hashCode")
    void shouldImplementHashCodeAfterNormalization() {
      // Given - 大写和小写标准化后应产生相同的 hashCode
      FileChecksum checksum1 = new FileChecksum("5D41402ABC4B2A76B9719D911017C592", null);
      FileChecksum checksum2 = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);

      // When & Then
      assertThat(checksum1.hashCode()).isEqualTo(checksum2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 多次调用返回相同值")
    void shouldImplementHashCodeConsistentlyAcrossMultipleCalls() {
      // Given
      FileChecksum checksum = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);

      // When
      int hashCode1 = checksum.hashCode();
      int hashCode2 = checksum.hashCode();
      int hashCode3 = checksum.hashCode();

      // Then
      assertThat(hashCode1).isEqualTo(hashCode2);
      assertThat(hashCode2).isEqualTo(hashCode3);
    }

    @Test
    @DisplayName("应该正确实现 toString - 包含两个哈希值")
    void shouldImplementToStringWithBothHashes() {
      // Given
      FileChecksum checksum =
          new FileChecksum(
              "5d41402abc4b2a76b9719d911017c592",
              "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");

      // When
      String toString = checksum.toString();

      // Then
      assertThat(toString)
          .contains("FileChecksum")
          .contains("5d41402abc4b2a76b9719d911017c592")
          .contains("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该正确实现 toString - 格式符合 record 规范")
    void shouldImplementToStringInRecordFormat() {
      // Given
      FileChecksum checksum = new FileChecksum("5d41402abc4b2a76b9719d911017c592", null);

      // When
      String toString = checksum.toString();

      // Then - record 的 toString 格式: ClassName[field1=value1, field2=value2]
      assertThat(toString)
          .startsWith("FileChecksum[")
          .endsWith("]")
          .contains("md5Hash=5d41402abc4b2a76b9719d911017c592")
          .contains("sha256Hash=null");
    }
  }

  // ========== 值对象不变性测试 ==========

  @Nested
  @DisplayName("值对象不变性")
  class ValueObjectImmutabilityTests {

    @Test
    @DisplayName("应该保证 MD5 哈希不可变")
    void shouldEnsureMd5HashIsImmutable() {
      // Given
      String originalMd5 = "5d41402abc4b2a76b9719d911017c592";
      FileChecksum checksum = new FileChecksum(originalMd5, null);

      // When - 获取哈希值
      String retrievedMd5 = checksum.md5Hash();

      // Then - 应该返回原始值（标准化后）
      assertThat(retrievedMd5).isEqualTo(originalMd5);
      assertThat(checksum.md5Hash()).isEqualTo(originalMd5); // 多次调用应该返回相同值
    }

    @Test
    @DisplayName("应该保证 SHA-256 哈希不可变")
    void shouldEnsureSha256HashIsImmutable() {
      // Given
      String originalSha256 =
          "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";
      FileChecksum checksum = new FileChecksum(null, originalSha256);

      // When - 获取哈希值
      String retrievedSha256 = checksum.sha256Hash();

      // Then - 应该返回原始值（标准化后）
      assertThat(retrievedSha256).isEqualTo(originalSha256);
      assertThat(checksum.sha256Hash()).isEqualTo(originalSha256); // 多次调用应该返回相同值
    }

    @Test
    @DisplayName("应该保证 FileChecksum 完全不可变")
    void shouldEnsureFileChecksumIsCompletelyImmutable() {
      // Given
      FileChecksum checksum =
          new FileChecksum(
              "5d41402abc4b2a76b9719d911017c592",
              "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");

      // When - 获取所有字段
      String md5 = checksum.md5Hash();
      String sha256 = checksum.sha256Hash();

      // Then - 所有字段应该保持原值
      assertThat(checksum.md5Hash()).isEqualTo(md5);
      assertThat(checksum.sha256Hash()).isEqualTo(sha256);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理标准 MD5 长度（32 字符）")
    void shouldHandleStandardMd5Length() {
      // Given - MD5 应该是 32 字符的十六进制字符串
      String md5Hash = "5d41402abc4b2a76b9719d911017c592";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).hasSize(32);
      assertThat(checksum.md5Hash()).matches("[a-f0-9]{32}");
    }

    @Test
    @DisplayName("应该处理标准 SHA-256 长度（64 字符）")
    void shouldHandleStandardSha256Length() {
      // Given - SHA-256 应该是 64 字符的十六进制字符串
      String sha256Hash =
          "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash()).hasSize(64);
      assertThat(checksum.sha256Hash()).matches("[a-f0-9]{64}");
    }

    @Test
    @DisplayName("应该处理只含数字的 MD5")
    void shouldHandleMd5WithOnlyDigits() {
      // Given
      String md5Hash = "12345678901234567890123456789012";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("12345678901234567890123456789012");
    }

    @Test
    @DisplayName("应该处理只含字母的 SHA-256")
    void shouldHandleSha256WithOnlyLetters() {
      // Given
      String sha256Hash =
          "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash())
          .isEqualTo("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd");
    }

    @Test
    @DisplayName("应该处理数字和字母混合的 MD5")
    void shouldHandleMd5WithMixedDigitsAndLetters() {
      // Given
      String md5Hash = "a1b2c3d4e5f6789012345678901234ab";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("a1b2c3d4e5f6789012345678901234ab");
    }

    @Test
    @DisplayName("应该处理数字和字母混合的 SHA-256")
    void shouldHandleSha256WithMixedDigitsAndLetters() {
      // Given
      String sha256Hash =
          "a1b2c3d4e5f67890a1b2c3d4e5f67890a1b2c3d4e5f67890a1b2c3d4e5f67890";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.sha256Hash())
          .isEqualTo("a1b2c3d4e5f67890a1b2c3d4e5f67890a1b2c3d4e5f67890a1b2c3d4e5f67890");
    }
  }

  // ========== 真实场景集成测试 ==========

  @Nested
  @DisplayName("真实场景集成测试")
  class RealWorldScenarioTests {

    @Test
    @DisplayName("应该正确处理 PDF 文件校验和场景（MD5 + SHA-256）")
    void shouldHandlePdfFileChecksumScenario() {
      // Given - 真实的 PDF 文件校验和
      String md5Hash = "d8e8fca2dc0f896fd7cb4cb0031ba249";
      String sha256Hash =
          "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, sha256Hash);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("d8e8fca2dc0f896fd7cb4cb0031ba249");
      assertThat(checksum.sha256Hash())
          .isEqualTo("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8");
    }

    @Test
    @DisplayName("应该正确处理只有 MD5 的场景（遗留系统）")
    void shouldHandleLegacySystemWithOnlyMd5() {
      // Given - 遗留系统可能只提供 MD5
      String md5Hash = "098f6bcd4621d373cade4e832627b4f6";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, null);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("098f6bcd4621d373cade4e832627b4f6");
      assertThat(checksum.sha256Hash()).isNull();
    }

    @Test
    @DisplayName("应该正确处理只有 SHA-256 的场景（现代系统）")
    void shouldHandleModernSystemWithOnlySha256() {
      // Given - 现代系统推荐使用 SHA-256
      String sha256Hash =
          "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

      // When
      FileChecksum checksum = new FileChecksum(null, sha256Hash);

      // Then
      assertThat(checksum.md5Hash()).isNull();
      assertThat(checksum.sha256Hash())
          .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("应该支持校验和比较场景（文件完整性验证）")
    void shouldSupportChecksumComparisonScenario() {
      // Given - 两个文件的校验和
      FileChecksum checksum1 =
          new FileChecksum(
              "5d41402abc4b2a76b9719d911017c592",
              "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
      FileChecksum checksum2 =
          new FileChecksum(
              "5D41402ABC4B2A76B9719D911017C592",
              "2C26B46B68FFC68FF99B453C1D30413413422D706483BFA0F98A5E886266E7AE");

      // When - 比较校验和（标准化后应该相等）
      boolean isSame = checksum1.equals(checksum2);

      // Then - 应该识别为相同文件（标准化后）
      assertThat(isSame).isTrue();
    }

    @Test
    @DisplayName("应该支持从外部系统接收的校验和（需要 trim 和标准化）")
    void shouldSupportChecksumFromExternalSystem() {
      // Given - 外部系统返回的可能包含空格和大写的校验和
      String rawMd5 = "  5D41402ABC4B2A76B9719D911017C592  ";
      String rawSha256 =
          "\t2C26B46B68FFC68FF99B453C1D30413413422D706483BFA0F98A5E886266E7AE\n";

      // When
      FileChecksum checksum = new FileChecksum(rawMd5, rawSha256);

      // Then - 应该自动标准化
      assertThat(checksum.md5Hash()).isEqualTo("5d41402abc4b2a76b9719d911017c592");
      assertThat(checksum.sha256Hash())
          .isEqualTo("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
    }

    @Test
    @DisplayName("应该支持医学文献 PDF 校验和场景")
    void shouldSupportMedicalLiteraturePdfChecksumScenario() {
      // Given - 医学文献 PDF 文件（来自 PubMed Central）
      String md5Hash = "a1234567890abcdef1234567890abcde";
      String sha256Hash =
          "f1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

      // When
      FileChecksum checksum = new FileChecksum(md5Hash, sha256Hash);

      // Then
      assertThat(checksum.md5Hash()).isEqualTo("a1234567890abcdef1234567890abcde");
      assertThat(checksum.sha256Hash())
          .isEqualTo("f1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    }
  }
}
