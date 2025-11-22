package com.patra.registry.domain.model.vo.provenance;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Provenance 值对象单元测试。
/// 
/// 测试策略：
/// 
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 测试 record 的业务约束验证（正整数 ID、非空白字符串等）
///   - 验证字符串字段自动 trim 处理
///   - 测试可选字段的 null 处理
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
/// 
/// 覆盖范围：
/// 
/// - ✅ record 构造函数验证测试
///   - ✅ 正整数 ID 验证（DomainValidationException.positive）
///   - ✅ 非空白字符串验证（code, name, timezoneDefault, lifecycleStatusCode）
///   - ✅ 字符串 trim 处理测试
///   - ✅ 可选字段处理（baseUrlDefault, docsUrl 允许为 null）
///   - ✅ isActive() 方法测试
///   - ✅ record 的 equals/hashCode/toString 测试
///   - ✅ 不变性保证
///   - ✅ 边界条件处理
/// 
/// @author Patra Team
/// @since 2.0
@DisplayName("Provenance 单元测试")
class ProvenanceTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的数据源")
    void shouldCreateProvenanceWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      String code = "pubmed";
      String name = "PubMed";
      String baseUrlDefault = "https://api.pubmed.org";
      String timezoneDefault = "UTC";
      String docsUrl = "https://docs.pubmed.org";
      boolean active = true;
      String lifecycleStatusCode = "ACTIVE";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(
              id,
              code,
              name,
              baseUrlDefault,
              timezoneDefault,
              docsUrl,
              active,
              lifecycleStatusCode);

      // Then: 验证所有字段正确赋值
      assertThat(provenance).isNotNull();
      assertThat(provenance.id()).isEqualTo(id);
      assertThat(provenance.code()).isEqualTo(code);
      assertThat(provenance.name()).isEqualTo(name);
      assertThat(provenance.baseUrlDefault()).isEqualTo(baseUrlDefault);
      assertThat(provenance.timezoneDefault()).isEqualTo(timezoneDefault);
      assertThat(provenance.docsUrl()).isEqualTo(docsUrl);
      assertThat(provenance.active()).isTrue();
      assertThat(provenance.lifecycleStatusCode()).isEqualTo(lifecycleStatusCode);
    }

    @Test
    @DisplayName("应该成功创建不包含可选字段的数据源")
    void shouldCreateProvenanceWithoutOptionalFields() {
      // Given: 可选字段为 null
      Long id = 1001L;
      String code = "pubmed";
      String name = "PubMed";
      String baseUrlDefault = null;
      String timezoneDefault = "UTC";
      String docsUrl = null;
      boolean active = true;
      String lifecycleStatusCode = "ACTIVE";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(
              id,
              code,
              name,
              baseUrlDefault,
              timezoneDefault,
              docsUrl,
              active,
              lifecycleStatusCode);

      // Then: 验证必需字段正确赋值，可选字段为 null
      assertThat(provenance).isNotNull();
      assertThat(provenance.id()).isEqualTo(id);
      assertThat(provenance.code()).isEqualTo(code);
      assertThat(provenance.name()).isEqualTo(name);
      assertThat(provenance.baseUrlDefault()).isNull();
      assertThat(provenance.timezoneDefault()).isEqualTo(timezoneDefault);
      assertThat(provenance.docsUrl()).isNull();
      assertThat(provenance.active()).isTrue();
      assertThat(provenance.lifecycleStatusCode()).isEqualTo(lifecycleStatusCode);
    }

    @Test
    @DisplayName("应该成功创建未激活状态的数据源")
    void shouldCreateInactiveProvenance() {
      // Given: active 为 false
      Long id = 1002L;
      String code = "crossref";
      String name = "Crossref";
      String baseUrlDefault = "https://api.crossref.org";
      String timezoneDefault = "UTC";
      String docsUrl = "https://docs.crossref.org";
      boolean active = false;
      String lifecycleStatusCode = "DEPRECATED";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(
              id,
              code,
              name,
              baseUrlDefault,
              timezoneDefault,
              docsUrl,
              active,
              lifecycleStatusCode);

      // Then: 验证 active 为 false
      assertThat(provenance.active()).isFalse();
      assertThat(provenance.lifecycleStatusCode()).isEqualTo("DEPRECATED");
    }
  }

  // ========== ID 验证测试 ==========

  @Nested
  @DisplayName("ID 正整数验证")
  class IdValidationTests {

    @Test
    @DisplayName("应该抛出异常当 ID 为 null")
    void shouldThrowExceptionWhenIdIsNull() {
      // Given: ID 为 null
      Long id = null;

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () -> new Provenance(id, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 ID 为 0")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given: ID 为 0
      Long id = 0L;

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () -> new Provenance(id, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 ID 为负数")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given: ID 为负数
      Long id = -1L;

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () -> new Provenance(id, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 ID 为 1 的数据源")
    void shouldCreateProvenanceWithIdOne() {
      // Given: ID 为 1
      Long id = 1L;

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(id, "test", "Test Source", null, "UTC", null, true, "ACTIVE");

      // Then: 验证成功创建
      assertThat(provenance.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 ID 为 Long.MAX_VALUE 的数据源")
    void shouldCreateProvenanceWithMaxId() {
      // Given: ID 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(id, "test", "Test Source", null, "UTC", null, true, "ACTIVE");

      // Then: 验证成功创建
      assertThat(provenance.id()).isEqualTo(Long.MAX_VALUE);
    }
  }

  // ========== Code 验证测试 ==========

  @Nested
  @DisplayName("Code 非空白验证")
  class CodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 code 为 null")
    void shouldThrowExceptionWhenCodeIsNull() {
      // Given: code 为 null
      String code = null;

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () -> new Provenance(1001L, code, "PubMed", null, "UTC", null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 code 为空字符串")
    void shouldThrowExceptionWhenCodeIsEmpty() {
      // Given: code 为空字符串
      String code = "";

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () -> new Provenance(1001L, code, "PubMed", null, "UTC", null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 code 仅包含空白字符")
    void shouldThrowExceptionWhenCodeIsBlank() {
      // Given: code 仅包含空白字符
      String code = "   ";

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () -> new Provenance(1001L, code, "PubMed", null, "UTC", null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim code 字段")
    void shouldTrimCode() {
      // Given: code 包含首尾空白
      String code = "  pubmed  ";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, code, "PubMed", null, "UTC", null, true, "ACTIVE");

      // Then: 验证 code 已被 trim
      assertThat(provenance.code()).isEqualTo("pubmed");
    }
  }

  // ========== Name 验证测试 ==========

  @Nested
  @DisplayName("Name 非空白验证")
  class NameValidationTests {

    @Test
    @DisplayName("应该抛出异常当 name 为 null")
    void shouldThrowExceptionWhenNameIsNull() {
      // Given: name 为 null
      String name = null;

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () -> new Provenance(1001L, "pubmed", name, null, "UTC", null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance name")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 name 为空字符串")
    void shouldThrowExceptionWhenNameIsEmpty() {
      // Given: name 为空字符串
      String name = "";

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () -> new Provenance(1001L, "pubmed", name, null, "UTC", null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance name")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 name 仅包含空白字符")
    void shouldThrowExceptionWhenNameIsBlank() {
      // Given: name 仅包含空白字符
      String name = "   ";

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () -> new Provenance(1001L, "pubmed", name, null, "UTC", null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance name")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim name 字段")
    void shouldTrimName() {
      // Given: name 包含首尾空白
      String name = "  PubMed  ";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "pubmed", name, null, "UTC", null, true, "ACTIVE");

      // Then: 验证 name 已被 trim
      assertThat(provenance.name()).isEqualTo("PubMed");
    }
  }

  // ========== TimezoneDefault 验证测试 ==========

  @Nested
  @DisplayName("TimezoneDefault 非空白验证")
  class TimezoneDefaultValidationTests {

    @Test
    @DisplayName("应该抛出异常当 timezoneDefault 为 null")
    void shouldThrowExceptionWhenTimezoneDefaultIsNull() {
      // Given: timezoneDefault 为 null
      String timezoneDefault = null;

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () ->
                  new Provenance(
                      1001L, "pubmed", "PubMed", null, timezoneDefault, null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Timezone")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 timezoneDefault 为空字符串")
    void shouldThrowExceptionWhenTimezoneDefaultIsEmpty() {
      // Given: timezoneDefault 为空字符串
      String timezoneDefault = "";

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () ->
                  new Provenance(
                      1001L, "pubmed", "PubMed", null, timezoneDefault, null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Timezone")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 timezoneDefault 仅包含空白字符")
    void shouldThrowExceptionWhenTimezoneDefaultIsBlank() {
      // Given: timezoneDefault 仅包含空白字符
      String timezoneDefault = "   ";

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () ->
                  new Provenance(
                      1001L, "pubmed", "PubMed", null, timezoneDefault, null, true, "ACTIVE"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Timezone")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim timezoneDefault 字段")
    void shouldTrimTimezoneDefault() {
      // Given: timezoneDefault 包含首尾空白
      String timezoneDefault = "  UTC  ";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", null, timezoneDefault, null, true, "ACTIVE");

      // Then: 验证 timezoneDefault 已被 trim
      assertThat(provenance.timezoneDefault()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("应该接受 IANA 格式的时区")
    void shouldAcceptIanaTimezone() {
      // Given: IANA 格式的时区
      String timezoneDefault = "Asia/Shanghai";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", null, timezoneDefault, null, true, "ACTIVE");

      // Then: 验证 timezoneDefault 正确赋值
      assertThat(provenance.timezoneDefault()).isEqualTo("Asia/Shanghai");
    }
  }

  // ========== LifecycleStatusCode 验证测试 ==========

  @Nested
  @DisplayName("LifecycleStatusCode 非空白验证")
  class LifecycleStatusCodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 lifecycleStatusCode 为 null")
    void shouldThrowExceptionWhenLifecycleStatusCodeIsNull() {
      // Given: lifecycleStatusCode 为 null
      String lifecycleStatusCode = null;

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () ->
                  new Provenance(
                      1001L, "pubmed", "PubMed", null, "UTC", null, true, lifecycleStatusCode))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Lifecycle status code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 lifecycleStatusCode 为空字符串")
    void shouldThrowExceptionWhenLifecycleStatusCodeIsEmpty() {
      // Given: lifecycleStatusCode 为空字符串
      String lifecycleStatusCode = "";

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () ->
                  new Provenance(
                      1001L, "pubmed", "PubMed", null, "UTC", null, true, lifecycleStatusCode))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Lifecycle status code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 lifecycleStatusCode 仅包含空白字符")
    void shouldThrowExceptionWhenLifecycleStatusCodeIsBlank() {
      // Given: lifecycleStatusCode 仅包含空白字符
      String lifecycleStatusCode = "   ";

      // When & Then: 创建数据源应该失败
      assertThatThrownBy(
              () ->
                  new Provenance(
                      1001L, "pubmed", "PubMed", null, "UTC", null, true, lifecycleStatusCode))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Lifecycle status code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim lifecycleStatusCode 字段")
    void shouldTrimLifecycleStatusCode() {
      // Given: lifecycleStatusCode 包含首尾空白
      String lifecycleStatusCode = "  ACTIVE  ";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, lifecycleStatusCode);

      // Then: 验证 lifecycleStatusCode 已被 trim
      assertThat(provenance.lifecycleStatusCode()).isEqualTo("ACTIVE");
    }
  }

  // ========== 可选字段测试 ==========

  @Nested
  @DisplayName("可选字段处理")
  class OptionalFieldsTests {

    @Test
    @DisplayName("baseUrlDefault 允许为 null")
    void baseUrlDefaultCanBeNull() {
      // Given: baseUrlDefault 为 null
      String baseUrlDefault = null;

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", baseUrlDefault, "UTC", null, true, "ACTIVE");

      // Then: 验证 baseUrlDefault 为 null
      assertThat(provenance.baseUrlDefault()).isNull();
    }

    @Test
    @DisplayName("应该自动 trim baseUrlDefault 字段（非 null 时）")
    void shouldTrimBaseUrlDefaultWhenNonNull() {
      // Given: baseUrlDefault 包含首尾空白
      String baseUrlDefault = "  https://api.pubmed.org  ";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", baseUrlDefault, "UTC", null, true, "ACTIVE");

      // Then: 验证 baseUrlDefault 已被 trim
      assertThat(provenance.baseUrlDefault()).isEqualTo("https://api.pubmed.org");
    }

    @Test
    @DisplayName("docsUrl 允许为 null")
    void docsUrlCanBeNull() {
      // Given: docsUrl 为 null
      String docsUrl = null;

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", docsUrl, true, "ACTIVE");

      // Then: 验证 docsUrl 为 null
      assertThat(provenance.docsUrl()).isNull();
    }

    @Test
    @DisplayName("应该自动 trim docsUrl 字段（非 null 时）")
    void shouldTrimDocsUrlWhenNonNull() {
      // Given: docsUrl 包含首尾空白
      String docsUrl = "  https://docs.pubmed.org  ";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", docsUrl, true, "ACTIVE");

      // Then: 验证 docsUrl 已被 trim
      assertThat(provenance.docsUrl()).isEqualTo("https://docs.pubmed.org");
    }

    @Test
    @DisplayName("应该处理所有可选字段都为 null 的情况")
    void shouldHandleAllOptionalFieldsBeingNull() {
      // Given: 所有可选字段为 null
      String baseUrlDefault = null;
      String docsUrl = null;

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", baseUrlDefault, "UTC", docsUrl, true, "ACTIVE");

      // Then: 验证可选字段都为 null
      assertThat(provenance.baseUrlDefault()).isNull();
      assertThat(provenance.docsUrl()).isNull();
    }
  }

  // ========== isActive 方法测试 ==========

  @Nested
  @DisplayName("isActive 方法")
  class IsActiveMethodTests {

    @Test
    @DisplayName("应该在 active 为 true 时返回 true")
    void shouldReturnTrueWhenActiveIsTrue() {
      // Given: active 为 true 的数据源
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      // When & Then: isActive 应该返回 true
      assertThat(provenance.isActive()).isTrue();
    }

    @Test
    @DisplayName("应该在 active 为 false 时返回 false")
    void shouldReturnFalseWhenActiveIsFalse() {
      // Given: active 为 false 的数据源
      Provenance provenance =
          new Provenance(1002L, "crossref", "Crossref", null, "UTC", null, false, "DEPRECATED");

      // When & Then: isActive 应该返回 false
      assertThat(provenance.isActive()).isFalse();
    }

    @Test
    @DisplayName("isActive 应该直接反映 active 字段的值")
    void isActiveShouldReflectActiveField() {
      // Given: 两个不同激活状态的数据源
      Provenance activeProvenance =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      Provenance inactiveProvenance =
          new Provenance(1002L, "crossref", "Crossref", null, "UTC", null, false, "DEPRECATED");

      // When & Then: isActive 应该与 active 字段一致
      assertThat(activeProvenance.isActive()).isEqualTo(activeProvenance.active());
      assertThat(inactiveProvenance.isActive()).isEqualTo(inactiveProvenance.active());
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该正确实现 equals 方法（相同值对象相等）")
    void shouldImplementEqualsCorrectly() {
      // Given: 两个相同值的数据源
      Provenance provenance1 =
          new Provenance(
              1001L,
              "pubmed",
              "PubMed",
              "https://api.pubmed.org",
              "UTC",
              "https://docs.pubmed.org",
              true,
              "ACTIVE");

      Provenance provenance2 =
          new Provenance(
              1001L,
              "pubmed",
              "PubMed",
              "https://api.pubmed.org",
              "UTC",
              "https://docs.pubmed.org",
              true,
              "ACTIVE");

      // When & Then: 应该相等
      assertThat(provenance1).isEqualTo(provenance2);
      assertThat(provenance1).hasSameHashCodeAs(provenance2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的数据源
      Provenance provenance1 =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      Provenance provenance2 =
          new Provenance(1002L, "crossref", "Crossref", null, "UTC", null, false, "DEPRECATED");

      // When & Then: 不应该相等
      assertThat(provenance1).isNotEqualTo(provenance2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的数据源
      Provenance provenance1 =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      Provenance provenance2 =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      // When & Then: hashCode 应该相等
      assertThat(provenance1.hashCode()).isEqualTo(provenance2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建数据源
      Provenance provenance =
          new Provenance(
              1001L,
              "pubmed",
              "PubMed",
              "https://api.pubmed.org",
              "UTC",
              "https://docs.pubmed.org",
              true,
              "ACTIVE");

      // When: 调用 toString
      String toString = provenance.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("Provenance");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("pubmed");
      assertThat(toString).contains("PubMed");
      assertThat(toString).contains("https://api.pubmed.org");
      assertThat(toString).contains("UTC");
      assertThat(toString).contains("https://docs.pubmed.org");
      assertThat(toString).contains("true");
      assertThat(toString).contains("ACTIVE");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建数据源
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      // When & Then: 对象应该等于自身
      assertThat(provenance).isEqualTo(provenance);
    }

    @Test
    @DisplayName("应该支持 equals 对称性")
    void shouldSupportEqualsSymmetry() {
      // Given: 两个相同值的数据源
      Provenance provenance1 =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      Provenance provenance2 =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      // When & Then: 对称性（a.equals(b) == b.equals(a)）
      assertThat(provenance1.equals(provenance2)).isEqualTo(provenance2.equals(provenance1));
      assertThat(provenance1).isEqualTo(provenance2);
      assertThat(provenance2).isEqualTo(provenance1);
    }

    @Test
    @DisplayName("应该支持 equals 传递性")
    void shouldSupportEqualsTransitivity() {
      // Given: 三个相同值的数据源
      Provenance provenance1 =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      Provenance provenance2 =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      Provenance provenance3 =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      // When & Then: 传递性（a.equals(b) && b.equals(c) => a.equals(c)）
      assertThat(provenance1).isEqualTo(provenance2);
      assertThat(provenance2).isEqualTo(provenance3);
      assertThat(provenance1).isEqualTo(provenance3);
    }

    @Test
    @DisplayName("应该正确处理与 null 的比较")
    void shouldHandleNullComparison() {
      // Given: 创建数据源
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      // When & Then: 与 null 比较应该返回 false
      assertThat(provenance).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确处理与不同类型对象的比较")
    void shouldHandleDifferentTypeComparison() {
      // Given: 创建数据源
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      // When & Then: 与不同类型对象比较应该返回 false
      assertThat(provenance).isNotEqualTo("Not a Provenance");
      assertThat(provenance).isNotEqualTo(1001L);
    }
  }

  // ========== 不变性测试 ==========

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 字段应该是不可变的")
    void recordFieldsShouldBeImmutable() {
      // Given: 创建数据源
      Long originalId = 1001L;
      String originalCode = "pubmed";
      String originalName = "PubMed";
      String originalBaseUrlDefault = "https://api.pubmed.org";
      String originalTimezoneDefault = "UTC";
      String originalDocsUrl = "https://docs.pubmed.org";
      boolean originalActive = true;
      String originalLifecycleStatusCode = "ACTIVE";

      Provenance provenance =
          new Provenance(
              originalId,
              originalCode,
              originalName,
              originalBaseUrlDefault,
              originalTimezoneDefault,
              originalDocsUrl,
              originalActive,
              originalLifecycleStatusCode);

      // When: 获取字段值
      Long retrievedId = provenance.id();
      String retrievedCode = provenance.code();
      String retrievedName = provenance.name();
      String retrievedBaseUrlDefault = provenance.baseUrlDefault();
      String retrievedTimezoneDefault = provenance.timezoneDefault();
      String retrievedDocsUrl = provenance.docsUrl();
      boolean retrievedActive = provenance.active();
      String retrievedLifecycleStatusCode = provenance.lifecycleStatusCode();

      // Then: 字段值应该保持不变
      assertThat(retrievedId).isEqualTo(originalId);
      assertThat(retrievedCode).isEqualTo(originalCode);
      assertThat(retrievedName).isEqualTo(originalName);
      assertThat(retrievedBaseUrlDefault).isEqualTo(originalBaseUrlDefault);
      assertThat(retrievedTimezoneDefault).isEqualTo(originalTimezoneDefault);
      assertThat(retrievedDocsUrl).isEqualTo(originalDocsUrl);
      assertThat(retrievedActive).isEqualTo(originalActive);
      assertThat(retrievedLifecycleStatusCode).isEqualTo(originalLifecycleStatusCode);
    }

    @Test
    @DisplayName("字符串字段应该在创建后保持不变")
    void stringFieldsShouldRemainUnchangedAfterCreation() {
      // Given: 创建数据源
      Provenance provenance =
          new Provenance(
              1001L,
              "pubmed",
              "PubMed",
              "https://api.pubmed.org",
              "UTC",
              "https://docs.pubmed.org",
              true,
              "ACTIVE");

      // When: 多次获取字段值
      String code1 = provenance.code();
      String code2 = provenance.code();
      String name1 = provenance.name();
      String name2 = provenance.name();

      // Then: 字段值应该保持一致
      assertThat(code1).isEqualTo(code2);
      assertThat(name1).isEqualTo(name2);
      assertThat(code1).isSameAs(code2);
      assertThat(name1).isSameAs(name2);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件处理")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 code 为极短字符串的情况")
    void shouldHandleMinimalCode() {
      // Given: code 为单字符
      String code = "a";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, code, "Test Source", null, "UTC", null, true, "ACTIVE");

      // Then: 应该成功创建
      assertThat(provenance.code()).isEqualTo("a");
    }

    @Test
    @DisplayName("应该处理 code 为极长字符串的情况")
    void shouldHandleVeryLongCode() {
      // Given: code 为长字符串
      String code = "a".repeat(255);

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, code, "Test Source", null, "UTC", null, true, "ACTIVE");

      // Then: 应该成功创建
      assertThat(provenance.code()).hasSize(255);
    }

    @Test
    @DisplayName("应该处理 name 为极短字符串的情况")
    void shouldHandleMinimalName() {
      // Given: name 为单字符
      String name = "A";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "test", name, null, "UTC", null, true, "ACTIVE");

      // Then: 应该成功创建
      assertThat(provenance.name()).isEqualTo("A");
    }

    @Test
    @DisplayName("应该处理 name 为极长字符串的情况")
    void shouldHandleVeryLongName() {
      // Given: name 为长字符串
      String name = "Test Source ".repeat(50);

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "test", name.trim(), null, "UTC", null, true, "ACTIVE");

      // Then: 应该成功创建
      assertThat(provenance.name()).isNotBlank();
    }

    @Test
    @DisplayName("应该处理包含特殊字符的 URL")
    void shouldHandleUrlWithSpecialCharacters() {
      // Given: 包含特殊字符的 URL
      String baseUrlDefault = "https://api.example.org/v1?key=value&lang=en";
      String docsUrl = "https://docs.example.org/api/reference#section";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(
              1001L, "test", "Test Source", baseUrlDefault, "UTC", docsUrl, true, "ACTIVE");

      // Then: 应该成功创建
      assertThat(provenance.baseUrlDefault()).isEqualTo(baseUrlDefault);
      assertThat(provenance.docsUrl()).isEqualTo(docsUrl);
    }

    @Test
    @DisplayName("应该处理仅包含一个字符的时区代码")
    void shouldHandleShortTimezone() {
      // Given: 仅包含一个字符的时区（非标准但技术上允许）
      String timezoneDefault = "Z";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "test", "Test Source", null, timezoneDefault, null, true, "ACTIVE");

      // Then: 应该成功创建
      assertThat(provenance.timezoneDefault()).isEqualTo("Z");
    }

    @Test
    @DisplayName("应该处理包含特殊字符的 lifecycleStatusCode")
    void shouldHandleLifecycleStatusCodeWithSpecialCharacters() {
      // Given: 包含下划线的 lifecycleStatusCode
      String lifecycleStatusCode = "ACTIVE_VERIFIED";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(
              1001L, "test", "Test Source", null, "UTC", null, true, lifecycleStatusCode);

      // Then: 应该成功创建
      assertThat(provenance.lifecycleStatusCode()).isEqualTo("ACTIVE_VERIFIED");
    }

    @Test
    @DisplayName("应该处理 trim 后相同的不同输入")
    void shouldHandleDifferentInputsWithSameTrimmedValue() {
      // Given: trim 后相同的不同输入
      Provenance provenance1 =
          new Provenance(1001L, "pubmed", "PubMed", null, "UTC", null, true, "ACTIVE");

      Provenance provenance2 =
          new Provenance(
              1001L, "  pubmed  ", "  PubMed  ", null, "  UTC  ", null, true, "  ACTIVE  ");

      // When & Then: trim 后应该相等
      assertThat(provenance1).isEqualTo(provenance2);
    }
  }

  // ========== 字符串 Trim 处理综合测试 ==========

  @Nested
  @DisplayName("字符串 Trim 处理")
  class StringTrimTests {

    @Test
    @DisplayName("应该 trim 所有必需字符串字段")
    void shouldTrimAllRequiredStringFields() {
      // Given: 所有必需字符串字段包含首尾空白
      String code = "  pubmed  ";
      String name = "  PubMed  ";
      String timezoneDefault = "  UTC  ";
      String lifecycleStatusCode = "  ACTIVE  ";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, code, name, null, timezoneDefault, null, true, lifecycleStatusCode);

      // Then: 验证所有字段都已被 trim
      assertThat(provenance.code()).isEqualTo("pubmed");
      assertThat(provenance.name()).isEqualTo("PubMed");
      assertThat(provenance.timezoneDefault()).isEqualTo("UTC");
      assertThat(provenance.lifecycleStatusCode()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应该 trim 所有可选字符串字段（非 null 时）")
    void shouldTrimAllOptionalStringFieldsWhenNonNull() {
      // Given: 所有可选字符串字段包含首尾空白
      String baseUrlDefault = "  https://api.pubmed.org  ";
      String docsUrl = "  https://docs.pubmed.org  ";

      // When: 创建 Provenance
      Provenance provenance =
          new Provenance(1001L, "pubmed", "PubMed", baseUrlDefault, "UTC", docsUrl, true, "ACTIVE");

      // Then: 验证可选字段也被 trim
      assertThat(provenance.baseUrlDefault()).isEqualTo("https://api.pubmed.org");
      assertThat(provenance.docsUrl()).isEqualTo("https://docs.pubmed.org");
    }

    @Test
    @DisplayName("应该处理混合空白字符的字符串")
    void shouldHandleStringWithMixedWhitespace() {
      // Given: 包含制表符、换行符等混合空白的字符串
      String code = "\t\n  pubmed  \t\n";
      String name = " \t PubMed \n ";

      // When: 创建 Provenance
      Provenance provenance = new Provenance(1001L, code, name, null, "UTC", null, true, "ACTIVE");

      // Then: 验证空白字符都被 trim
      assertThat(provenance.code()).isEqualTo("pubmed");
      assertThat(provenance.name()).isEqualTo("PubMed");
    }

    @Test
    @DisplayName("应该保留字符串内部的空白字符")
    void shouldPreserveInternalWhitespace() {
      // Given: 字符串内部包含空白字符
      String code = "  pub med  ";
      String name = "  Pub Med  ";

      // When: 创建 Provenance
      Provenance provenance = new Provenance(1001L, code, name, null, "UTC", null, true, "ACTIVE");

      // Then: 验证内部空白字符被保留
      assertThat(provenance.code()).isEqualTo("pub med");
      assertThat(provenance.name()).isEqualTo("Pub Med");
    }
  }
}
