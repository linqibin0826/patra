package com.patra.objectstorage.domain.model.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// StorageProvider 枚举单元测试。
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
/// - ✅ 枚举值存在性测试（MINIO, S3, OSS, COS）
///   - ✅ values() 方法测试（数量、包含关系、顺序）
///   - ✅ valueOf() 方法测试（有效字符串、无效字符串、null）
///   - ✅ name() 方法测试（枚举名称验证）
///   - ✅ ordinal() 方法测试（枚举序号验证）
///   - ✅ fromName() 自定义方法测试（大小写不敏感、异常场景）
///   - ✅ equals() 和 == 比较测试
///   - ✅ 枚举单例不变性测试
///   - ✅ 真实场景集成测试（存储提供商选择、配置解析）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("StorageProvider 枚举单元测试")
class StorageProviderTest {

  // ========== 枚举值存在性测试 ==========

  @Nested
  @DisplayName("枚举值存在性")
  class EnumValueExistenceTests {

    @Test
    @DisplayName("应该存在 MINIO 枚举值")
    void shouldHaveMinioEnumValue() {
      // Given & When
      StorageProvider provider = StorageProvider.MINIO;

      // Then
      assertThat(provider).isNotNull();
      assertThat(provider).isEqualTo(StorageProvider.MINIO);
    }

    @Test
    @DisplayName("应该存在 S3 枚举值")
    void shouldHaveS3EnumValue() {
      // Given & When
      StorageProvider provider = StorageProvider.S3;

      // Then
      assertThat(provider).isNotNull();
      assertThat(provider).isEqualTo(StorageProvider.S3);
    }

    @Test
    @DisplayName("应该存在 OSS 枚举值")
    void shouldHaveOssEnumValue() {
      // Given & When
      StorageProvider provider = StorageProvider.OSS;

      // Then
      assertThat(provider).isNotNull();
      assertThat(provider).isEqualTo(StorageProvider.OSS);
    }

    @Test
    @DisplayName("应该存在 COS 枚举值")
    void shouldHaveCosEnumValue() {
      // Given & When
      StorageProvider provider = StorageProvider.COS;

      // Then
      assertThat(provider).isNotNull();
      assertThat(provider).isEqualTo(StorageProvider.COS);
    }

    @Test
    @DisplayName("应该有且仅有四个枚举值")
    void shouldHaveExactlyFourEnumValues() {
      // Given & When
      StorageProvider[] values = StorageProvider.values();

      // Then
      assertThat(values).hasSize(4);
    }
  }

  // ========== values() 方法测试 ==========

  @Nested
  @DisplayName("values() 方法")
  class ValuesMethodTests {

    @Test
    @DisplayName("应该返回包含 4 个枚举值的数组")
    void shouldReturnArrayWithFourValues() {
      // Given & When
      StorageProvider[] values = StorageProvider.values();

      // Then
      assertThat(values).hasSize(4);
    }

    @Test
    @DisplayName("应该返回包含所有枚举值的数组")
    void shouldReturnArrayContainingAllEnumValues() {
      // Given & When
      StorageProvider[] values = StorageProvider.values();

      // Then
      assertThat(values)
          .containsExactlyInAnyOrder(
              StorageProvider.MINIO, StorageProvider.S3, StorageProvider.OSS, StorageProvider.COS);
    }

    @Test
    @DisplayName("应该按声明顺序返回枚举值（MINIO, S3, OSS, COS）")
    void shouldReturnEnumValuesInDeclarationOrder() {
      // Given & When
      StorageProvider[] values = StorageProvider.values();

      // Then
      assertThat(values)
          .containsExactly(
              StorageProvider.MINIO, StorageProvider.S3, StorageProvider.OSS, StorageProvider.COS);
    }

    @Test
    @DisplayName("values() 应该每次返回新数组（防御性拷贝）")
    void valuesShouldReturnNewArrayEachTime() {
      // Given
      StorageProvider[] values1 = StorageProvider.values();
      StorageProvider[] values2 = StorageProvider.values();

      // When - 修改第一个数组
      values1[0] = StorageProvider.COS;

      // Then - 第二个数组应该不受影响
      assertThat(values2[0]).isEqualTo(StorageProvider.MINIO);
      assertThat(values1).isNotSameAs(values2); // 不是同一个数组对象
    }

    @Test
    @DisplayName("values() 应该返回不可变的枚举值内容")
    void valuesShouldReturnImmutableEnumContent() {
      // Given
      StorageProvider[] values = StorageProvider.values();
      StorageProvider originalFirstValue = values[0];

      // When - 尝试修改数组元素（虽然数组本身可修改，但枚举值不可变）
      values[0] = StorageProvider.S3;

      // Then - 原始枚举常量应该保持不变
      assertThat(StorageProvider.MINIO).isEqualTo(originalFirstValue);
      assertThat(StorageProvider.values()[0]).isEqualTo(StorageProvider.MINIO);
    }
  }

  // ========== valueOf() 方法测试 ==========

  @Nested
  @DisplayName("valueOf() 方法")
  class ValueOfMethodTests {

    @Test
    @DisplayName("应该通过字符串 'MINIO' 获取 MINIO 枚举值")
    void shouldGetMinioEnumFromStringMinio() {
      // Given
      String providerName = "MINIO";

      // When
      StorageProvider provider = StorageProvider.valueOf(providerName);

      // Then
      assertThat(provider).isEqualTo(StorageProvider.MINIO);
    }

    @Test
    @DisplayName("应该通过字符串 'S3' 获取 S3 枚举值")
    void shouldGetS3EnumFromStringS3() {
      // Given
      String providerName = "S3";

      // When
      StorageProvider provider = StorageProvider.valueOf(providerName);

      // Then
      assertThat(provider).isEqualTo(StorageProvider.S3);
    }

    @Test
    @DisplayName("应该通过字符串 'OSS' 获取 OSS 枚举值")
    void shouldGetOssEnumFromStringOss() {
      // Given
      String providerName = "OSS";

      // When
      StorageProvider provider = StorageProvider.valueOf(providerName);

      // Then
      assertThat(provider).isEqualTo(StorageProvider.OSS);
    }

    @Test
    @DisplayName("应该通过字符串 'COS' 获取 COS 枚举值")
    void shouldGetCosEnumFromStringCos() {
      // Given
      String providerName = "COS";

      // When
      StorageProvider provider = StorageProvider.valueOf(providerName);

      // Then
      assertThat(provider).isEqualTo(StorageProvider.COS);
    }

    @Test
    @DisplayName("应该抛出 IllegalArgumentException 当传入无效字符串")
    void shouldThrowIllegalArgumentExceptionForInvalidString() {
      // Given
      String invalidName = "AZURE";

      // When & Then
      assertThatThrownBy(() -> StorageProvider.valueOf(invalidName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No enum constant");
    }

    @Test
    @DisplayName("应该抛出 IllegalArgumentException 当传入空字符串")
    void shouldThrowIllegalArgumentExceptionForEmptyString() {
      // Given
      String emptyName = "";

      // When & Then
      assertThatThrownBy(() -> StorageProvider.valueOf(emptyName))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("应该抛出 NullPointerException 当传入 null")
    void shouldThrowNullPointerExceptionForNull() {
      // Given
      String nullName = null;

      // When & Then
      assertThatThrownBy(() -> StorageProvider.valueOf(nullName))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("应该区分大小写（小写字符串应失败）")
    void shouldBeCaseSensitive() {
      // Given
      String lowerCaseName = "minio";

      // When & Then
      assertThatThrownBy(() -> StorageProvider.valueOf(lowerCaseName))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("应该区分大小写（混合大小写字符串应失败）")
    void shouldBeCaseSensitiveForMixedCase() {
      // Given
      String mixedCaseName = "Minio";

      // When & Then
      assertThatThrownBy(() -> StorageProvider.valueOf(mixedCaseName))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("应该拒绝包含前后空格的字符串")
    void shouldRejectStringWithLeadingOrTrailingSpaces() {
      // Given
      String nameWithSpaces = " S3 ";

      // When & Then
      assertThatThrownBy(() -> StorageProvider.valueOf(nameWithSpaces))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ========== name() 方法测试 ==========

  @Nested
  @DisplayName("name() 方法")
  class NameMethodTests {

    @Test
    @DisplayName("MINIO.name() 应该返回 'MINIO'")
    void minioNameShouldReturnMinio() {
      // Given
      StorageProvider provider = StorageProvider.MINIO;

      // When
      String name = provider.name();

      // Then
      assertThat(name).isEqualTo("MINIO");
    }

    @Test
    @DisplayName("S3.name() 应该返回 'S3'")
    void s3NameShouldReturnS3() {
      // Given
      StorageProvider provider = StorageProvider.S3;

      // When
      String name = provider.name();

      // Then
      assertThat(name).isEqualTo("S3");
    }

    @Test
    @DisplayName("OSS.name() 应该返回 'OSS'")
    void ossNameShouldReturnOss() {
      // Given
      StorageProvider provider = StorageProvider.OSS;

      // When
      String name = provider.name();

      // Then
      assertThat(name).isEqualTo("OSS");
    }

    @Test
    @DisplayName("COS.name() 应该返回 'COS'")
    void cosNameShouldReturnCos() {
      // Given
      StorageProvider provider = StorageProvider.COS;

      // When
      String name = provider.name();

      // Then
      assertThat(name).isEqualTo("COS");
    }

    @Test
    @DisplayName("name() 应该与 valueOf() 兼容（往返转换）")
    void nameShouldBeCompatibleWithValueOf() {
      // Given
      StorageProvider originalProvider = StorageProvider.S3;

      // When - 通过 name() 获取字符串，再通过 valueOf() 转回枚举
      String name = originalProvider.name();
      StorageProvider reconstructedProvider = StorageProvider.valueOf(name);

      // Then
      assertThat(reconstructedProvider).isEqualTo(originalProvider);
    }

    @Test
    @DisplayName("name() 应该始终返回相同的字符串（幂等性）")
    void nameShouldAlwaysReturnSameString() {
      // Given
      StorageProvider provider = StorageProvider.MINIO;

      // When
      String name1 = provider.name();
      String name2 = provider.name();
      String name3 = provider.name();

      // Then - 幂等性
      assertThat(name1).isEqualTo(name2);
      assertThat(name2).isEqualTo(name3);
      assertThat(name1).isSameAs(name2); // 字符串常量应该是同一个对象
    }
  }

  // ========== ordinal() 方法测试 ==========

  @Nested
  @DisplayName("ordinal() 方法")
  class OrdinalMethodTests {

    @Test
    @DisplayName("MINIO.ordinal() 应该返回 0")
    void minioOrdinalShouldBeZero() {
      // Given
      StorageProvider provider = StorageProvider.MINIO;

      // When
      int ordinal = provider.ordinal();

      // Then
      assertThat(ordinal).isEqualTo(0);
    }

    @Test
    @DisplayName("S3.ordinal() 应该返回 1")
    void s3OrdinalShouldBeOne() {
      // Given
      StorageProvider provider = StorageProvider.S3;

      // When
      int ordinal = provider.ordinal();

      // Then
      assertThat(ordinal).isEqualTo(1);
    }

    @Test
    @DisplayName("OSS.ordinal() 应该返回 2")
    void ossOrdinalShouldBeTwo() {
      // Given
      StorageProvider provider = StorageProvider.OSS;

      // When
      int ordinal = provider.ordinal();

      // Then
      assertThat(ordinal).isEqualTo(2);
    }

    @Test
    @DisplayName("COS.ordinal() 应该返回 3")
    void cosOrdinalShouldBeThree() {
      // Given
      StorageProvider provider = StorageProvider.COS;

      // When
      int ordinal = provider.ordinal();

      // Then
      assertThat(ordinal).isEqualTo(3);
    }

    @Test
    @DisplayName("ordinal() 应该按声明顺序递增")
    void ordinalShouldIncreaseInDeclarationOrder() {
      // Given & When
      int minioOrdinal = StorageProvider.MINIO.ordinal();
      int s3Ordinal = StorageProvider.S3.ordinal();
      int ossOrdinal = StorageProvider.OSS.ordinal();
      int cosOrdinal = StorageProvider.COS.ordinal();

      // Then
      assertThat(minioOrdinal).isLessThan(s3Ordinal);
      assertThat(s3Ordinal).isLessThan(ossOrdinal);
      assertThat(ossOrdinal).isLessThan(cosOrdinal);
      assertThat(cosOrdinal - minioOrdinal).isEqualTo(3);
    }

    @Test
    @DisplayName("ordinal() 应该从 0 开始连续递增")
    void ordinalShouldStartFromZeroAndBeContiguous() {
      // Given
      StorageProvider[] values = StorageProvider.values();

      // When & Then - 验证序号从 0 开始且连续
      for (int i = 0; i < values.length; i++) {
        assertThat(values[i].ordinal()).isEqualTo(i);
      }
    }

    @Test
    @DisplayName("ordinal() 应该始终返回相同的值（幂等性）")
    void ordinalShouldAlwaysReturnSameValue() {
      // Given
      StorageProvider provider = StorageProvider.S3;

      // When
      int ordinal1 = provider.ordinal();
      int ordinal2 = provider.ordinal();
      int ordinal3 = provider.ordinal();

      // Then - 幂等性
      assertThat(ordinal1).isEqualTo(ordinal2);
      assertThat(ordinal2).isEqualTo(ordinal3);
    }
  }

  // ========== fromName() 自定义方法测试（重点） ==========

  @Nested
  @DisplayName("fromName() 自定义方法")
  class FromNameMethodTests {

    @Nested
    @DisplayName("有效输入场景")
    class ValidInputTests {

      @Test
      @DisplayName("应该通过 'MINIO'（大写）解析 MINIO 枚举值")
      void shouldParseMinioFromUpperCase() {
        // Given
        String providerName = "MINIO";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.MINIO);
      }

      @Test
      @DisplayName("应该通过 'S3'（大写）解析 S3 枚举值")
      void shouldParseS3FromUpperCase() {
        // Given
        String providerName = "S3";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.S3);
      }

      @Test
      @DisplayName("应该通过 'OSS'（大写）解析 OSS 枚举值")
      void shouldParseOssFromUpperCase() {
        // Given
        String providerName = "OSS";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.OSS);
      }

      @Test
      @DisplayName("应该通过 'COS'（大写）解析 COS 枚举值")
      void shouldParseCosFromUpperCase() {
        // Given
        String providerName = "COS";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.COS);
      }
    }

    @Nested
    @DisplayName("大小写不敏感场景")
    class CaseInsensitiveTests {

      @Test
      @DisplayName("应该通过 'minio'（小写）解析 MINIO 枚举值")
      void shouldParseMinioFromLowerCase() {
        // Given
        String providerName = "minio";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.MINIO);
      }

      @Test
      @DisplayName("应该通过 's3'（小写）解析 S3 枚举值")
      void shouldParseS3FromLowerCase() {
        // Given
        String providerName = "s3";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.S3);
      }

      @Test
      @DisplayName("应该通过 'oss'（小写）解析 OSS 枚举值")
      void shouldParseOssFromLowerCase() {
        // Given
        String providerName = "oss";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.OSS);
      }

      @Test
      @DisplayName("应该通过 'cos'（小写）解析 COS 枚举值")
      void shouldParseCosFromLowerCase() {
        // Given
        String providerName = "cos";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.COS);
      }

      @Test
      @DisplayName("应该通过 'MinIO'（混合大小写）解析 MINIO 枚举值")
      void shouldParseMinioFromMixedCase() {
        // Given
        String providerName = "MinIO";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.MINIO);
      }

      @Test
      @DisplayName("应该通过 'Oss'（混合大小写）解析 OSS 枚举值")
      void shouldParseOssFromMixedCase() {
        // Given
        String providerName = "Oss";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.OSS);
      }

      @Test
      @DisplayName("应该通过 'Cos'（混合大小写）解析 COS 枚举值")
      void shouldParseCosFromMixedCase() {
        // Given
        String providerName = "Cos";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.COS);
      }

      @Test
      @DisplayName("应该通过 'MiNiO'（混合大小写）解析 MINIO 枚举值")
      void shouldParseMinioFromRandomCase() {
        // Given
        String providerName = "MiNiO";

        // When
        StorageProvider provider = StorageProvider.fromName(providerName);

        // Then
        assertThat(provider).isEqualTo(StorageProvider.MINIO);
      }
    }

    @Nested
    @DisplayName("异常场景")
    class ExceptionTests {

      @Test
      @DisplayName("应该抛出 IllegalArgumentException 当传入不支持的提供商 'AZURE'")
      void shouldThrowExceptionForAzure() {
        // Given
        String invalidName = "AZURE";

        // When & Then
        assertThatThrownBy(() -> StorageProvider.fromName(invalidName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("不支持的存储提供商: AZURE");
      }

      @Test
      @DisplayName("应该抛出 IllegalArgumentException 当传入不支持的提供商 'GCS'")
      void shouldThrowExceptionForGcs() {
        // Given
        String invalidName = "GCS";

        // When & Then
        assertThatThrownBy(() -> StorageProvider.fromName(invalidName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("不支持的存储提供商: GCS");
      }

      @Test
      @DisplayName("应该抛出 IllegalArgumentException 当传入不支持的提供商 'unknown'")
      void shouldThrowExceptionForUnknown() {
        // Given
        String invalidName = "unknown";

        // When & Then
        assertThatThrownBy(() -> StorageProvider.fromName(invalidName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("不支持的存储提供商: unknown");
      }

      @Test
      @DisplayName("应该抛出 IllegalArgumentException 当传入 null")
      void shouldThrowExceptionForNull() {
        // Given
        String nullName = null;

        // When & Then - fromName() 内部使用 Stream API，null 会被处理为不匹配任何枚举值
        assertThatThrownBy(() -> StorageProvider.fromName(nullName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的存储提供商");
      }

      @Test
      @DisplayName("应该抛出 IllegalArgumentException 当传入空字符串")
      void shouldThrowExceptionForEmptyString() {
        // Given
        String emptyName = "";

        // When & Then
        assertThatThrownBy(() -> StorageProvider.fromName(emptyName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("不支持的存储提供商: ");
      }

      @Test
      @DisplayName("应该抛出 IllegalArgumentException 当传入空白字符串")
      void shouldThrowExceptionForBlankString() {
        // Given
        String blankName = "   ";

        // When & Then
        assertThatThrownBy(() -> StorageProvider.fromName(blankName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("不支持的存储提供商:    ");
      }

      @Test
      @DisplayName("应该抛出 IllegalArgumentException 当传入包含前后空格的无效字符串")
      void shouldThrowExceptionForInvalidStringWithSpaces() {
        // Given
        String nameWithSpaces = " INVALID ";

        // When & Then
        assertThatThrownBy(() -> StorageProvider.fromName(nameWithSpaces))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("不支持的存储提供商:  INVALID ");
      }
    }

    @Nested
    @DisplayName("边界场景")
    class EdgeCaseTests {

      @Test
      @DisplayName("应该正确处理与 valueOf() 不同的行为（fromName 不区分大小写）")
      void shouldHandleDifferentBehaviorFromValueOf() {
        // Given
        String lowerCaseName = "minio";

        // When
        StorageProvider fromNameResult = StorageProvider.fromName(lowerCaseName);

        // Then - fromName 应该成功（不区分大小写）
        assertThat(fromNameResult).isEqualTo(StorageProvider.MINIO);

        // 但 valueOf 应该失败（区分大小写）
        assertThatThrownBy(() -> StorageProvider.valueOf(lowerCaseName))
            .isInstanceOf(IllegalArgumentException.class);
      }

      @Test
      @DisplayName("应该保持幂等性（多次调用返回相同结果）")
      void shouldBeIdempotent() {
        // Given
        String providerName = "minio";

        // When
        StorageProvider result1 = StorageProvider.fromName(providerName);
        StorageProvider result2 = StorageProvider.fromName(providerName);
        StorageProvider result3 = StorageProvider.fromName(providerName);

        // Then - 应该返回相同的枚举值（单例）
        assertThat(result1).isSameAs(result2);
        assertThat(result2).isSameAs(result3);
        assertThat(result1).isEqualTo(StorageProvider.MINIO);
      }
    }
  }

  // ========== 枚举语义测试（equals、==、单例） ==========

  @Nested
  @DisplayName("枚举语义（equals、==、单例）")
  class EnumSemanticsTests {

    @Test
    @DisplayName("应该支持使用 == 比较枚举值")
    void shouldSupportIdentityComparisonWithDoubleEquals() {
      // Given
      StorageProvider provider1 = StorageProvider.MINIO;
      StorageProvider provider2 = StorageProvider.MINIO;

      // When
      boolean isSame = (provider1 == provider2);

      // Then
      assertThat(isSame).isTrue();
      assertThat(provider1).isSameAs(provider2); // AssertJ 验证
    }

    @Test
    @DisplayName("应该支持使用 == 比较不同枚举值")
    void shouldSupportIdentityComparisonForDifferentValues() {
      // Given
      StorageProvider minio = StorageProvider.MINIO;
      StorageProvider s3 = StorageProvider.S3;

      // When
      boolean isSame = (minio == s3);

      // Then
      assertThat(isSame).isFalse();
      assertThat(minio).isNotSameAs(s3);
    }

    @Test
    @DisplayName("应该支持使用 equals() 比较枚举值")
    void shouldSupportEqualsComparison() {
      // Given
      StorageProvider provider1 = StorageProvider.S3;
      StorageProvider provider2 = StorageProvider.S3;

      // When
      boolean isEqual = provider1.equals(provider2);

      // Then
      assertThat(isEqual).isTrue();
      assertThat(provider1).isEqualTo(provider2);
    }

    @Test
    @DisplayName("应该支持使用 equals() 比较不同枚举值")
    void shouldSupportEqualsComparisonForDifferentValues() {
      // Given
      StorageProvider oss = StorageProvider.OSS;
      StorageProvider cos = StorageProvider.COS;

      // When
      boolean isEqual = oss.equals(cos);

      // Then
      assertThat(isEqual).isFalse();
      assertThat(oss).isNotEqualTo(cos);
    }

    @Test
    @DisplayName("equals() 应该正确处理 null")
    void equalsShouldHandleNull() {
      // Given
      StorageProvider provider = StorageProvider.MINIO;

      // When
      boolean isEqual = provider.equals(null);

      // Then
      assertThat(isEqual).isFalse();
      assertThat(provider).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals() 应该正确处理不同类型对象")
    void equalsShouldHandleDifferentType() {
      // Given
      StorageProvider provider = StorageProvider.S3;
      Object other = "S3";

      // When
      boolean isEqual = provider.equals(other);

      // Then
      assertThat(isEqual).isFalse();
      assertThat(provider).isNotEqualTo(other);
    }

    @Test
    @DisplayName("枚举值应该是单例（同一枚举值始终是同一对象）")
    void enumValueShouldBeSingleton() {
      // Given
      StorageProvider provider1 = StorageProvider.MINIO;
      StorageProvider provider2 = StorageProvider.valueOf("MINIO");
      StorageProvider provider3 = StorageProvider.fromName("minio");
      StorageProvider provider4 = StorageProvider.values()[0];

      // When & Then - 所有引用应该指向同一对象
      assertThat(provider1).isSameAs(provider2);
      assertThat(provider2).isSameAs(provider3);
      assertThat(provider3).isSameAs(provider4);
      assertThat(provider1).isSameAs(provider4);
    }

    @Test
    @DisplayName("枚举值应该实现自反性（x.equals(x) 应该为 true）")
    void enumValueShouldImplementReflexivity() {
      // Given
      StorageProvider provider = StorageProvider.OSS;

      // When
      boolean isEqual = provider.equals(provider);

      // Then
      assertThat(isEqual).isTrue();
      assertThat(provider).isEqualTo(provider);
    }

    @Test
    @DisplayName("枚举值应该实现对称性（x.equals(y) == y.equals(x)）")
    void enumValueShouldImplementSymmetry() {
      // Given
      StorageProvider provider1 = StorageProvider.COS;
      StorageProvider provider2 = StorageProvider.COS;

      // When
      boolean equals1 = provider1.equals(provider2);
      boolean equals2 = provider2.equals(provider1);

      // Then
      assertThat(equals1).isEqualTo(equals2);
      assertThat(provider1).isEqualTo(provider2);
      assertThat(provider2).isEqualTo(provider1);
    }

    @Test
    @DisplayName("枚举值应该实现传递性（x.equals(y) && y.equals(z) => x.equals(z)）")
    void enumValueShouldImplementTransitivity() {
      // Given
      StorageProvider provider1 = StorageProvider.S3;
      StorageProvider provider2 = StorageProvider.valueOf("S3");
      StorageProvider provider3 = StorageProvider.fromName("s3");

      // When & Then - 如果 x == y 且 y == z，则 x == z
      assertThat(provider1).isEqualTo(provider2);
      assertThat(provider2).isEqualTo(provider3);
      assertThat(provider1).isEqualTo(provider3);
    }

    @Test
    @DisplayName("相同枚举值应该有相同的 hashCode")
    void sameEnumValueShouldHaveSameHashCode() {
      // Given
      StorageProvider provider1 = StorageProvider.MINIO;
      StorageProvider provider2 = StorageProvider.MINIO;

      // When
      int hashCode1 = provider1.hashCode();
      int hashCode2 = provider2.hashCode();

      // Then - 相等的对象必须有相同的 hashCode
      assertThat(hashCode1).isEqualTo(hashCode2);
    }

    @Test
    @DisplayName("hashCode() 应该始终返回相同的值（幂等性）")
    void hashCodeShouldAlwaysReturnSameValue() {
      // Given
      StorageProvider provider = StorageProvider.OSS;

      // When
      int hashCode1 = provider.hashCode();
      int hashCode2 = provider.hashCode();
      int hashCode3 = provider.hashCode();

      // Then
      assertThat(hashCode1).isEqualTo(hashCode2);
      assertThat(hashCode2).isEqualTo(hashCode3);
    }
  }

  // ========== 枚举不变性测试 ==========

  @Nested
  @DisplayName("枚举不变性")
  class EnumImmutabilityTests {

    @Test
    @DisplayName("枚举值应该是不可变的常量")
    void enumValueShouldBeImmutableConstant() {
      // Given
      StorageProvider originalProvider = StorageProvider.S3;

      // When - 多次访问枚举值
      StorageProvider accessedProvider1 = StorageProvider.S3;
      StorageProvider accessedProvider2 = StorageProvider.S3;

      // Then - 应该始终返回同一对象
      assertThat(accessedProvider1).isSameAs(originalProvider);
      assertThat(accessedProvider2).isSameAs(originalProvider);
    }

    @Test
    @DisplayName("枚举值应该线程安全（多线程访问返回同一对象）")
    void enumValueShouldBeThreadSafe() throws InterruptedException {
      // Given
      final StorageProvider[] capturedProviders = new StorageProvider[2];

      // When - 在多个线程中访问枚举值
      Thread thread1 =
          new Thread(
              () -> {
                capturedProviders[0] = StorageProvider.MINIO;
              });
      Thread thread2 =
          new Thread(
              () -> {
                capturedProviders[1] = StorageProvider.MINIO;
              });

      thread1.start();
      thread2.start();
      thread1.join();
      thread2.join();

      // Then - 两个线程应该获取到同一对象
      assertThat(capturedProviders[0]).isSameAs(capturedProviders[1]);
    }

    @Test
    @DisplayName("values() 返回的数组修改不应影响枚举本身")
    void modifyingValuesArrayShouldNotAffectEnum() {
      // Given
      StorageProvider[] values = StorageProvider.values();
      StorageProvider originalFirstValue = values[0];

      // When - 修改返回的数组
      values[0] = StorageProvider.COS;

      // Then - 枚举本身应该不受影响
      assertThat(StorageProvider.MINIO).isEqualTo(originalFirstValue);
      assertThat(StorageProvider.values()[0]).isEqualTo(StorageProvider.MINIO);
    }
  }

  // ========== 真实场景集成测试 ==========

  @Nested
  @DisplayName("真实场景集成测试")
  class RealWorldScenarioTests {

    @Test
    @DisplayName("应该正确表示 MinIO 存储提供商")
    void shouldRepresentMinioStorageProvider() {
      // Given - 使用 MinIO 作为对象存储
      StorageProvider provider = StorageProvider.MINIO;

      // When & Then
      assertThat(provider).isEqualTo(StorageProvider.MINIO);
      assertThat(provider.name()).isEqualTo("MINIO");
      assertThat(provider.ordinal()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该正确表示 Amazon S3 存储提供商")
    void shouldRepresentS3StorageProvider() {
      // Given - 使用 Amazon S3 作为对象存储
      StorageProvider provider = StorageProvider.S3;

      // When & Then
      assertThat(provider).isEqualTo(StorageProvider.S3);
      assertThat(provider.name()).isEqualTo("S3");
      assertThat(provider.ordinal()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该正确表示阿里云 OSS 存储提供商")
    void shouldRepresentOssStorageProvider() {
      // Given - 使用阿里云 OSS 作为对象存储
      StorageProvider provider = StorageProvider.OSS;

      // When & Then
      assertThat(provider).isEqualTo(StorageProvider.OSS);
      assertThat(provider.name()).isEqualTo("OSS");
      assertThat(provider.ordinal()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该正确表示腾讯云 COS 存储提供商")
    void shouldRepresentCosStorageProvider() {
      // Given - 使用腾讯云 COS 作为对象存储
      StorageProvider provider = StorageProvider.COS;

      // When & Then
      assertThat(provider).isEqualTo(StorageProvider.COS);
      assertThat(provider.name()).isEqualTo("COS");
      assertThat(provider.ordinal()).isEqualTo(3);
    }

    @Test
    @DisplayName("应该支持从配置文件解析存储提供商（不区分大小写）")
    void shouldSupportParsingFromConfigFile() {
      // Given - 从配置文件读取 storage.provider=minio
      String configValue = "minio";

      // When
      StorageProvider provider = StorageProvider.fromName(configValue);

      // Then
      assertThat(provider).isEqualTo(StorageProvider.MINIO);
    }

    @Test
    @DisplayName("应该支持用户输入的提供商选择（不区分大小写）")
    void shouldSupportUserInputProviderSelection() {
      // Given - 用户输入 "S3"
      String userInput = "S3";

      // When
      StorageProvider provider = StorageProvider.fromName(userInput);

      // Then
      assertThat(provider).isEqualTo(StorageProvider.S3);
    }

    @Test
    @DisplayName("应该支持基于提供商的条件逻辑判断")
    void shouldSupportConditionalLogicBasedOnProvider() {
      // Given - 根据存储提供商执行不同的业务逻辑
      StorageProvider provider = StorageProvider.MINIO;

      // When - 使用 switch 表达式（Java 14+）
      String endpoint =
          switch (provider) {
            case MINIO -> "http://localhost:9000";
            case S3 -> "https://s3.amazonaws.com";
            case OSS -> "https://oss-cn-hangzhou.aliyuncs.com";
            case COS -> "https://cos.ap-guangzhou.myqcloud.com";
          };

      // Then
      assertThat(endpoint).isEqualTo("http://localhost:9000");
    }

    @Test
    @DisplayName("应该支持存储提供商序列化场景（枚举 -> 字符串）")
    void shouldSupportProviderSerializationToString() {
      // Given - 需要将枚举序列化为字符串存储到数据库
      StorageProvider provider = StorageProvider.OSS;

      // When
      String serialized = provider.name();

      // Then
      assertThat(serialized).isEqualTo("OSS");
    }

    @Test
    @DisplayName("应该支持存储提供商反序列化场景（字符串 -> 枚举）")
    void shouldSupportProviderDeserializationFromString() {
      // Given - 从数据库读取字符串，需要反序列化为枚举
      String serialized = "COS";

      // When
      StorageProvider provider = StorageProvider.valueOf(serialized);

      // Then
      assertThat(provider).isEqualTo(StorageProvider.COS);
    }

    @Test
    @DisplayName("应该支持完整的序列化-反序列化往返转换")
    void shouldSupportCompleteSerializationDeserialization() {
      // Given - 完整的往返转换场景
      StorageProvider originalProvider = StorageProvider.S3;

      // When - 序列化 -> 反序列化
      String serialized = originalProvider.name();
      StorageProvider deserialized = StorageProvider.valueOf(serialized);

      // Then - 应该恢复为原始枚举值
      assertThat(deserialized).isEqualTo(originalProvider);
      assertThat(deserialized).isSameAs(originalProvider); // 单例保证
    }

    @Test
    @DisplayName("应该支持配置验证场景（拒绝不支持的提供商）")
    void shouldSupportConfigValidation() {
      // Given - 用户尝试配置不支持的存储提供商
      String unsupportedProvider = "AZURE";

      // When & Then - 应该拒绝并抛出明确的异常
      assertThatThrownBy(() -> StorageProvider.fromName(unsupportedProvider))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("不支持的存储提供商: AZURE");
    }

    @Test
    @DisplayName("应该支持多提供商环境切换场景")
    void shouldSupportMultiProviderEnvironmentSwitching() {
      // Given - 在开发环境使用 MinIO，生产环境使用 S3
      String devConfig = "minio";
      String prodConfig = "s3";

      // When
      StorageProvider devProvider = StorageProvider.fromName(devConfig);
      StorageProvider prodProvider = StorageProvider.fromName(prodConfig);

      // Then
      assertThat(devProvider).isEqualTo(StorageProvider.MINIO);
      assertThat(prodProvider).isEqualTo(StorageProvider.S3);
      assertThat(devProvider).isNotEqualTo(prodProvider);
    }

    @Test
    @DisplayName("应该支持提供商检查场景（是否为云存储）")
    void shouldSupportCloudProviderCheck() {
      // Given - 检查是否为云存储提供商（S3/OSS/COS）
      StorageProvider minio = StorageProvider.MINIO;
      StorageProvider s3 = StorageProvider.S3;
      StorageProvider oss = StorageProvider.OSS;
      StorageProvider cos = StorageProvider.COS;

      // When
      boolean minioIsCloud =
          (minio == StorageProvider.S3
              || minio == StorageProvider.OSS
              || minio == StorageProvider.COS);
      boolean s3IsCloud =
          (s3 == StorageProvider.S3 || s3 == StorageProvider.OSS || s3 == StorageProvider.COS);
      boolean ossIsCloud =
          (oss == StorageProvider.S3 || oss == StorageProvider.OSS || oss == StorageProvider.COS);
      boolean cosIsCloud =
          (cos == StorageProvider.S3 || cos == StorageProvider.OSS || cos == StorageProvider.COS);

      // Then
      assertThat(minioIsCloud).isFalse(); // MinIO 是开源自建
      assertThat(s3IsCloud).isTrue();
      assertThat(ossIsCloud).isTrue();
      assertThat(cosIsCloud).isTrue();
    }
  }
}
