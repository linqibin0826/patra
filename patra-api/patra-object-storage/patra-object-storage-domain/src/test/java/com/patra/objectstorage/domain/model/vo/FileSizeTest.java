package com.patra.objectstorage.domain.model.vo;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * FileSize 值对象单元测试。
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
 *   <li>✅ 非负字节数验证（bytes >= 0）
 *   <li>✅ 负数字节抛出 IllegalArgumentException
 *   <li>✅ humanReadable() 方法测试（B/KB/MB/GB 单位转换）
 *   <li>✅ 边界值测试（0, 1023, 1024, 1024*1024-1, etc.）
 *   <li>✅ record 自动生成的 equals/hashCode/toString 测试
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("FileSize 值对象单元测试")
class FileSizeTest {

  // ========== 构造函数验证测试 ==========

  @Nested
  @DisplayName("构造函数验证")
  class ConstructorValidationTests {

    @Test
    @DisplayName("应该成功创建 FileSize 当字节数为 0")
    void shouldCreateFileSizeWhenBytesIsZero() {
      // Given
      long bytes = 0L;

      // When
      FileSize fileSize = new FileSize(bytes);

      // Then
      assertThat(fileSize).isNotNull();
      assertThat(fileSize.bytes()).isEqualTo(0L);
    }

    @Test
    @DisplayName("应该成功创建 FileSize 当字节数为正数")
    void shouldCreateFileSizeWhenBytesIsPositive() {
      // Given
      long bytes = 1024L;

      // When
      FileSize fileSize = new FileSize(bytes);

      // Then
      assertThat(fileSize).isNotNull();
      assertThat(fileSize.bytes()).isEqualTo(1024L);
    }

    @Test
    @DisplayName("应该成功创建 FileSize 当字节数为 Long.MAX_VALUE")
    void shouldCreateFileSizeWhenBytesIsMaxValue() {
      // Given
      long bytes = Long.MAX_VALUE;

      // When
      FileSize fileSize = new FileSize(bytes);

      // Then
      assertThat(fileSize).isNotNull();
      assertThat(fileSize.bytes()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("应该抛出异常当字节数为负数")
    void shouldThrowExceptionWhenBytesIsNegative() {
      // Given
      long bytes = -1L;

      // When & Then
      assertThatThrownBy(() -> new FileSize(bytes))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("文件大小必须 >= 0 字节");
    }

    @Test
    @DisplayName("应该抛出异常当字节数为 Long.MIN_VALUE")
    void shouldThrowExceptionWhenBytesIsMinValue() {
      // Given
      long bytes = Long.MIN_VALUE;

      // When & Then
      assertThatThrownBy(() -> new FileSize(bytes))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("文件大小必须 >= 0 字节");
    }

    @Test
    @DisplayName("应该抛出异常当字节数为 -1024")
    void shouldThrowExceptionWhenBytesIsNegative1024() {
      // Given
      long bytes = -1024L;

      // When & Then
      assertThatThrownBy(() -> new FileSize(bytes))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("文件大小必须 >= 0 字节");
    }
  }

  // ========== humanReadable() 方法测试 ==========

  @Nested
  @DisplayName("humanReadable() 方法 - 字节单位 (B)")
  class HumanReadableMethodBytesTests {

    @Test
    @DisplayName("应该返回 '0 B' 当字节数为 0")
    void shouldReturn0BWhenBytesIsZero() {
      // Given
      FileSize fileSize = new FileSize(0L);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("0 B");
    }

    @Test
    @DisplayName("应该返回 '1 B' 当字节数为 1")
    void shouldReturn1BWhenBytesIs1() {
      // Given
      FileSize fileSize = new FileSize(1L);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("1 B");
    }

    @Test
    @DisplayName("应该返回 '512 B' 当字节数为 512")
    void shouldReturn512BWhenBytesIs512() {
      // Given
      FileSize fileSize = new FileSize(512L);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("512 B");
    }

    @Test
    @DisplayName("应该返回 '1023 B' 当字节数为 1023（边界值）")
    void shouldReturn1023BWhenBytesIs1023() {
      // Given - 1024 之前的最大字节数
      FileSize fileSize = new FileSize(1023L);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("1023 B");
    }
  }

  @Nested
  @DisplayName("humanReadable() 方法 - KB 单位")
  class HumanReadableMethodKilobytesTests {

    @Test
    @DisplayName("应该返回 '1.00 KB' 当字节数为 1024（边界值）")
    void shouldReturn1KBWhenBytesIs1024() {
      // Given - 正好 1 KB
      FileSize fileSize = new FileSize(1024L);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("1.00 KB");
    }

    @Test
    @DisplayName("应该返回 '1.50 KB' 当字节数为 1536")
    void shouldReturn1Dot50KBWhenBytesIs1536() {
      // Given - 1.5 KB
      FileSize fileSize = new FileSize(1536L);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("1.50 KB");
    }

    @Test
    @DisplayName("应该返回 '2.00 KB' 当字节数为 2048")
    void shouldReturn2KBWhenBytesIs2048() {
      // Given - 2 KB
      FileSize fileSize = new FileSize(2048L);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("2.00 KB");
    }

    @Test
    @DisplayName("应该返回 '1024.00 KB' 当字节数为 1024*1024-1（边界值）")
    void shouldReturn1024KBWhenBytesIs1MBMinus1() {
      // Given - 1 MB 之前的最大字节数
      FileSize fileSize = new FileSize(1024L * 1024 - 1);

      // When
      String readable = fileSize.humanReadable();

      // Then - 由于整除，(1024*1024-1)/1024.0 ≈ 1024.00
      assertThat(readable).isEqualTo("1024.00 KB");
    }

    @Test
    @DisplayName("应该使用英文 Locale 格式化小数点")
    void shouldUseEnglishLocaleForDecimalPoint() {
      // Given - 确保使用点号而非逗号
      FileSize fileSize = new FileSize(1536L); // 1.5 KB

      // When
      String readable = fileSize.humanReadable();

      // Then - 应该使用点号（.）而非逗号（,）
      assertThat(readable).contains(".");
      assertThat(readable).doesNotContain(",");
    }
  }

  @Nested
  @DisplayName("humanReadable() 方法 - MB 单位")
  class HumanReadableMethodMegabytesTests {

    @Test
    @DisplayName("应该返回 '1.00 MB' 当字节数为 1024*1024（边界值）")
    void shouldReturn1MBWhenBytesIs1MB() {
      // Given - 正好 1 MB
      FileSize fileSize = new FileSize(1024L * 1024);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("1.00 MB");
    }

    @Test
    @DisplayName("应该返回 '2.00 MB' 当字节数为 2*1024*1024")
    void shouldReturn2MBWhenBytesIs2MB() {
      // Given - 2 MB
      FileSize fileSize = new FileSize(2L * 1024 * 1024);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("2.00 MB");
    }

    @Test
    @DisplayName("应该返回 '1.50 MB' 当字节数为 1.5*1024*1024")
    void shouldReturn1Dot50MBWhenBytesIs1Dot5MB() {
      // Given - 1.5 MB
      FileSize fileSize = new FileSize((long) (1.5 * 1024 * 1024));

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("1.50 MB");
    }

    @Test
    @DisplayName("应该返回 '100.00 MB' 当字节数为 100*1024*1024")
    void shouldReturn100MBWhenBytesIs100MB() {
      // Given - 100 MB
      FileSize fileSize = new FileSize(100L * 1024 * 1024);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("100.00 MB");
    }

    @Test
    @DisplayName("应该返回 '1024.00 MB' 当字节数为 1024*1024*1024-1（边界值）")
    void shouldReturn1024MBWhenBytesIs1GBMinus1() {
      // Given - 1 GB 之前的最大字节数
      FileSize fileSize = new FileSize(1024L * 1024 * 1024 - 1);

      // When
      String readable = fileSize.humanReadable();

      // Then - 由于整除，(1024*1024*1024-1)/(1024.0*1024) ≈ 1024.00
      assertThat(readable).isEqualTo("1024.00 MB");
    }

    @Test
    @DisplayName("应该正确格式化为两位小数")
    void shouldFormatToTwoDecimalPlaces() {
      // Given - 1.234 MB (精确值会被格式化为 1.23 MB)
      FileSize fileSize = new FileSize((long) (1.234 * 1024 * 1024));

      // When
      String readable = fileSize.humanReadable();

      // Then - 应该只显示两位小数
      assertThat(readable).matches("\\d+\\.\\d{2} MB");
    }
  }

  @Nested
  @DisplayName("humanReadable() 方法 - GB 单位")
  class HumanReadableMethodGigabytesTests {

    @Test
    @DisplayName("应该返回 '1.00 GB' 当字节数为 1024*1024*1024（边界值）")
    void shouldReturn1GBWhenBytesIs1GB() {
      // Given - 正好 1 GB
      FileSize fileSize = new FileSize(1024L * 1024 * 1024);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("1.00 GB");
    }

    @Test
    @DisplayName("应该返回 '2.00 GB' 当字节数为 2*1024*1024*1024")
    void shouldReturn2GBWhenBytesIs2GB() {
      // Given - 2 GB
      FileSize fileSize = new FileSize(2L * 1024 * 1024 * 1024);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("2.00 GB");
    }

    @Test
    @DisplayName("应该返回 '1.50 GB' 当字节数为 1.5*1024*1024*1024")
    void shouldReturn1Dot50GBWhenBytesIs1Dot5GB() {
      // Given - 1.5 GB
      FileSize fileSize = new FileSize((long) (1.5 * 1024 * 1024 * 1024));

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("1.50 GB");
    }

    @Test
    @DisplayName("应该返回 '10.00 GB' 当字节数为 10*1024*1024*1024")
    void shouldReturn10GBWhenBytesIs10GB() {
      // Given - 10 GB
      FileSize fileSize = new FileSize(10L * 1024 * 1024 * 1024);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("10.00 GB");
    }

    @Test
    @DisplayName("应该返回 '100.00 GB' 当字节数为 100*1024*1024*1024")
    void shouldReturn100GBWhenBytesIs100GB() {
      // Given - 100 GB
      FileSize fileSize = new FileSize(100L * 1024 * 1024 * 1024);

      // When
      String readable = fileSize.humanReadable();

      // Then
      assertThat(readable).isEqualTo("100.00 GB");
    }

    @Test
    @DisplayName("应该返回 '1000.00 GB' 当字节数为 1000*1024*1024*1024")
    void shouldReturn1000GBWhenBytesIs1000GB() {
      // Given - 1000 GB (接近 1 TB 但不转换为 TB)
      FileSize fileSize = new FileSize(1000L * 1024 * 1024 * 1024);

      // When
      String readable = fileSize.humanReadable();

      // Then - 应该仍然显示为 GB
      assertThat(readable).isEqualTo("1000.00 GB");
    }

    @Test
    @DisplayName("应该正确格式化超大文件（接近 Long.MAX_VALUE）")
    void shouldFormatVeryLargeFileSizeNearMaxValue() {
      // Given - 接近 Long.MAX_VALUE 的文件大小（约 8 EB）
      long bytes = Long.MAX_VALUE; // 9,223,372,036,854,775,807 bytes

      // When
      FileSize fileSize = new FileSize(bytes);
      String readable = fileSize.humanReadable();

      // Then - 应该以 GB 为单位显示
      assertThat(readable).endsWith(" GB");
      assertThat(readable).matches("\\d+\\.\\d{2} GB");
    }
  }

  @Nested
  @DisplayName("humanReadable() 方法 - 幂等性测试")
  class HumanReadableMethodIdempotencyTests {

    @Test
    @DisplayName("humanReadable() 应该始终返回相同的结果")
    void humanReadableShouldAlwaysReturnSameResult() {
      // Given
      FileSize fileSize = new FileSize(1024L * 1024); // 1 MB

      // When
      String readable1 = fileSize.humanReadable();
      String readable2 = fileSize.humanReadable();
      String readable3 = fileSize.humanReadable();

      // Then - 幂等性
      assertThat(readable1).isEqualTo(readable2);
      assertThat(readable2).isEqualTo(readable3);
      assertThat(readable1).isEqualTo("1.00 MB");
    }
  }

  // ========== record 自动生成方法测试 ==========

  @Nested
  @DisplayName("record 自动生成的 equals/hashCode/toString")
  class RecordGeneratedMethodsTests {

    @Test
    @DisplayName("应该正确实现 equals - 相同的字节数")
    void shouldImplementEqualsCorrectlyForSameBytes() {
      // Given
      FileSize fileSize1 = new FileSize(1024L);
      FileSize fileSize2 = new FileSize(1024L);

      // When & Then
      assertThat(fileSize1).isEqualTo(fileSize2);
      assertThat(fileSize2).isEqualTo(fileSize1); // 对称性
    }

    @Test
    @DisplayName("应该正确实现 equals - 不同的字节数")
    void shouldImplementEqualsCorrectlyForDifferentBytes() {
      // Given
      FileSize fileSize1 = new FileSize(1024L);
      FileSize fileSize2 = new FileSize(2048L);

      // When & Then
      assertThat(fileSize1).isNotEqualTo(fileSize2);
    }

    @Test
    @DisplayName("应该正确实现 equals - 自反性")
    void shouldImplementEqualsReflexivity() {
      // Given
      FileSize fileSize = new FileSize(1024L);

      // When & Then
      assertThat(fileSize).isEqualTo(fileSize);
    }

    @Test
    @DisplayName("应该正确实现 equals - 传递性")
    void shouldImplementEqualsTransitivity() {
      // Given
      FileSize fileSize1 = new FileSize(1024L);
      FileSize fileSize2 = new FileSize(1024L);
      FileSize fileSize3 = new FileSize(1024L);

      // When & Then - 如果 a == b 且 b == c，则 a == c
      assertThat(fileSize1).isEqualTo(fileSize2);
      assertThat(fileSize2).isEqualTo(fileSize3);
      assertThat(fileSize1).isEqualTo(fileSize3);
    }

    @Test
    @DisplayName("应该正确实现 equals - 与 null 比较")
    void shouldImplementEqualsWithNull() {
      // Given
      FileSize fileSize = new FileSize(1024L);

      // When & Then
      assertThat(fileSize).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确实现 equals - 与不同类型比较")
    void shouldImplementEqualsWithDifferentType() {
      // Given
      FileSize fileSize = new FileSize(1024L);
      Object other = 1024L; // Long 对象

      // When & Then
      assertThat(fileSize).isNotEqualTo(other);
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 相同的对象产生相同的 hashCode")
    void shouldImplementHashCodeConsistentlyForEqualObjects() {
      // Given
      FileSize fileSize1 = new FileSize(1024L);
      FileSize fileSize2 = new FileSize(1024L);

      // When & Then - 相等的对象必须有相同的 hashCode
      assertThat(fileSize1.hashCode()).isEqualTo(fileSize2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 hashCode - 多次调用返回相同值")
    void shouldImplementHashCodeConsistentlyAcrossMultipleCalls() {
      // Given
      FileSize fileSize = new FileSize(1024L);

      // When
      int hashCode1 = fileSize.hashCode();
      int hashCode2 = fileSize.hashCode();
      int hashCode3 = fileSize.hashCode();

      // Then
      assertThat(hashCode1).isEqualTo(hashCode2);
      assertThat(hashCode2).isEqualTo(hashCode3);
    }

    @Test
    @DisplayName("应该正确实现 toString - 包含字节数")
    void shouldImplementToStringWithBytes() {
      // Given
      FileSize fileSize = new FileSize(1024L);

      // When
      String toString = fileSize.toString();

      // Then
      assertThat(toString).contains("FileSize").contains("1024");
    }

    @Test
    @DisplayName("应该正确实现 toString - 格式符合 record 规范")
    void shouldImplementToStringInRecordFormat() {
      // Given
      FileSize fileSize = new FileSize(2048L);

      // When
      String toString = fileSize.toString();

      // Then - record 的 toString 格式: ClassName[field=value]
      assertThat(toString).startsWith("FileSize[").endsWith("]").contains("bytes=2048");
    }
  }

  // ========== 值对象不变性测试 ==========

  @Nested
  @DisplayName("值对象不变性")
  class ValueObjectImmutabilityTests {

    @Test
    @DisplayName("应该保证字节数不可变")
    void shouldEnsureBytesIsImmutable() {
      // Given
      long originalBytes = 1024L;
      FileSize fileSize = new FileSize(originalBytes);

      // When - 获取字节数
      long retrievedBytes = fileSize.bytes();

      // Then - 应该返回原始值
      assertThat(retrievedBytes).isEqualTo(originalBytes);
      assertThat(fileSize.bytes()).isEqualTo(originalBytes); // 多次调用应该返回相同值
    }

    @Test
    @DisplayName("应该保证 FileSize 完全不可变")
    void shouldEnsureFileSizeIsCompletelyImmutable() {
      // Given
      FileSize fileSize = new FileSize(1024L);
      long originalBytes = fileSize.bytes();
      String originalReadable = fileSize.humanReadable();

      // When - 多次调用方法
      fileSize.bytes();
      fileSize.humanReadable();

      // Then - 所有值应该保持不变
      assertThat(fileSize.bytes()).isEqualTo(originalBytes);
      assertThat(fileSize.humanReadable()).isEqualTo(originalReadable);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 0 字节文件")
    void shouldHandleZeroByteFile() {
      // Given
      FileSize fileSize = new FileSize(0L);

      // When & Then
      assertThat(fileSize.bytes()).isEqualTo(0L);
      assertThat(fileSize.humanReadable()).isEqualTo("0 B");
    }

    @Test
    @DisplayName("应该处理 1 字节文件")
    void shouldHandleOneByteFile() {
      // Given
      FileSize fileSize = new FileSize(1L);

      // When & Then
      assertThat(fileSize.bytes()).isEqualTo(1L);
      assertThat(fileSize.humanReadable()).isEqualTo("1 B");
    }

    @Test
    @DisplayName("应该处理 KB 边界 (1023 vs 1024)")
    void shouldHandleKBBoundary() {
      // Given
      FileSize size1023 = new FileSize(1023L);
      FileSize size1024 = new FileSize(1024L);

      // When & Then
      assertThat(size1023.humanReadable()).isEqualTo("1023 B");
      assertThat(size1024.humanReadable()).isEqualTo("1.00 KB");
    }

    @Test
    @DisplayName("应该处理 MB 边界 (1024*1024-1 vs 1024*1024)")
    void shouldHandleMBBoundary() {
      // Given
      FileSize sizeMBMinus1 = new FileSize(1024L * 1024 - 1);
      FileSize size1MB = new FileSize(1024L * 1024);

      // When & Then
      assertThat(sizeMBMinus1.humanReadable()).isEqualTo("1024.00 KB");
      assertThat(size1MB.humanReadable()).isEqualTo("1.00 MB");
    }

    @Test
    @DisplayName("应该处理 GB 边界 (1024*1024*1024-1 vs 1024*1024*1024)")
    void shouldHandleGBBoundary() {
      // Given
      FileSize sizeGBMinus1 = new FileSize(1024L * 1024 * 1024 - 1);
      FileSize size1GB = new FileSize(1024L * 1024 * 1024);

      // When & Then
      assertThat(sizeGBMinus1.humanReadable()).isEqualTo("1024.00 MB");
      assertThat(size1GB.humanReadable()).isEqualTo("1.00 GB");
    }

    @Test
    @DisplayName("应该处理超大文件 (Long.MAX_VALUE)")
    void shouldHandleMaxLongValue() {
      // Given
      FileSize fileSize = new FileSize(Long.MAX_VALUE);

      // When & Then
      assertThat(fileSize.bytes()).isEqualTo(Long.MAX_VALUE);
      assertThat(fileSize.humanReadable()).endsWith(" GB");
    }

    @Test
    @DisplayName("应该处理典型文件大小 - 1 KB")
    void shouldHandleTypicalFileSize1KB() {
      // Given
      FileSize fileSize = new FileSize(1024L);

      // When & Then
      assertThat(fileSize.humanReadable()).isEqualTo("1.00 KB");
    }

    @Test
    @DisplayName("应该处理典型文件大小 - 500 KB")
    void shouldHandleTypicalFileSize500KB() {
      // Given
      FileSize fileSize = new FileSize(500L * 1024);

      // When & Then
      assertThat(fileSize.humanReadable()).isEqualTo("500.00 KB");
    }

    @Test
    @DisplayName("应该处理典型文件大小 - 1 MB")
    void shouldHandleTypicalFileSize1MB() {
      // Given
      FileSize fileSize = new FileSize(1024L * 1024);

      // When & Then
      assertThat(fileSize.humanReadable()).isEqualTo("1.00 MB");
    }

    @Test
    @DisplayName("应该处理典型文件大小 - 10 MB")
    void shouldHandleTypicalFileSize10MB() {
      // Given
      FileSize fileSize = new FileSize(10L * 1024 * 1024);

      // When & Then
      assertThat(fileSize.humanReadable()).isEqualTo("10.00 MB");
    }

    @Test
    @DisplayName("应该处理典型文件大小 - 1 GB")
    void shouldHandleTypicalFileSize1GB() {
      // Given
      FileSize fileSize = new FileSize(1024L * 1024 * 1024);

      // When & Then
      assertThat(fileSize.humanReadable()).isEqualTo("1.00 GB");
    }

    @Test
    @DisplayName("应该处理典型文件大小 - 5 GB")
    void shouldHandleTypicalFileSize5GB() {
      // Given
      FileSize fileSize = new FileSize(5L * 1024 * 1024 * 1024);

      // When & Then
      assertThat(fileSize.humanReadable()).isEqualTo("5.00 GB");
    }
  }

  // ========== 真实场景集成测试 ==========

  @Nested
  @DisplayName("真实场景集成测试")
  class RealWorldScenarioTests {

    @Test
    @DisplayName("应该正确处理空文件场景")
    void shouldHandleEmptyFileScenario() {
      // Given - 空文件
      FileSize fileSize = new FileSize(0L);

      // When & Then
      assertThat(fileSize.bytes()).isEqualTo(0L);
      assertThat(fileSize.humanReadable()).isEqualTo("0 B");
    }

    @Test
    @DisplayName("应该正确处理小型文本文件场景 (2 KB)")
    void shouldHandleSmallTextFileScenario() {
      // Given - 小型 .txt 文件 (约 2 KB)
      FileSize fileSize = new FileSize(2048L);

      // When & Then
      assertThat(fileSize.bytes()).isEqualTo(2048L);
      assertThat(fileSize.humanReadable()).isEqualTo("2.00 KB");
    }

    @Test
    @DisplayName("应该正确处理中型图片文件场景 (500 KB)")
    void shouldHandleMediumImageFileScenario() {
      // Given - 中型 .jpg 图片 (约 500 KB)
      FileSize fileSize = new FileSize(512L * 1024);

      // When & Then
      assertThat(fileSize.bytes()).isEqualTo(512L * 1024);
      assertThat(fileSize.humanReadable()).isEqualTo("512.00 KB");
    }

    @Test
    @DisplayName("应该正确处理 PDF 文档场景 (2.5 MB)")
    void shouldHandlePDFDocumentScenario() {
      // Given - PDF 文档 (约 2.5 MB)
      FileSize fileSize = new FileSize((long) (2.5 * 1024 * 1024));

      // When & Then
      assertThat(fileSize.bytes()).isEqualTo((long) (2.5 * 1024 * 1024));
      assertThat(fileSize.humanReadable()).isEqualTo("2.50 MB");
    }

    @Test
    @DisplayName("应该正确处理医学出版物 PDF 场景 (5 MB)")
    void shouldHandleMedicalLiteraturePDFScenario() {
      // Given - 医学出版物 PDF (约 5 MB)
      FileSize fileSize = new FileSize(5L * 1024 * 1024);

      // When & Then
      assertThat(fileSize.bytes()).isEqualTo(5L * 1024 * 1024);
      assertThat(fileSize.humanReadable()).isEqualTo("5.00 MB");
    }

    @Test
    @DisplayName("应该正确处理视频文件场景 (1.5 GB)")
    void shouldHandleVideoFileScenario() {
      // Given - 视频文件 (约 1.5 GB)
      FileSize fileSize = new FileSize((long) (1.5 * 1024 * 1024 * 1024));

      // When & Then
      assertThat(fileSize.bytes()).isEqualTo((long) (1.5 * 1024 * 1024 * 1024));
      assertThat(fileSize.humanReadable()).isEqualTo("1.50 GB");
    }

    @Test
    @DisplayName("应该正确处理大型数据库备份场景 (10 GB)")
    void shouldHandleLargeDatabaseBackupScenario() {
      // Given - 数据库备份文件 (约 10 GB)
      FileSize fileSize = new FileSize(10L * 1024 * 1024 * 1024);

      // When & Then
      assertThat(fileSize.bytes()).isEqualTo(10L * 1024 * 1024 * 1024);
      assertThat(fileSize.humanReadable()).isEqualTo("10.00 GB");
    }

    @Test
    @DisplayName("应该支持文件大小比较场景")
    void shouldSupportFileSizeComparisonScenario() {
      // Given - 两个不同大小的文件
      FileSize smallFile = new FileSize(1024L); // 1 KB
      FileSize largeFile = new FileSize(1024L * 1024); // 1 MB

      // When - 比较文件大小
      boolean isSame = smallFile.equals(largeFile);
      boolean isSmaller = smallFile.bytes() < largeFile.bytes();

      // Then
      assertThat(isSame).isFalse();
      assertThat(isSmaller).isTrue();
    }

    @Test
    @DisplayName("应该支持文件大小聚合场景")
    void shouldSupportFileSizeAggregationScenario() {
      // Given - 多个文件大小
      FileSize file1 = new FileSize(1024L); // 1 KB
      FileSize file2 = new FileSize(2048L); // 2 KB
      FileSize file3 = new FileSize(3072L); // 3 KB

      // When - 计算总大小
      long totalBytes = file1.bytes() + file2.bytes() + file3.bytes();
      FileSize totalSize = new FileSize(totalBytes);

      // Then
      assertThat(totalSize.bytes()).isEqualTo(6144L);
      assertThat(totalSize.humanReadable()).isEqualTo("6.00 KB");
    }
  }
}
