package com.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// OrganizationType 枚举测试。
///
/// 基于 ROR Schema v2.0 的机构类型定义：
/// archive, company, education, facility, funder, government, healthcare, nonprofit, other
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationType 枚举测试")
class OrganizationTypeTest {

  @Nested
  @DisplayName("枚举值验证")
  class EnumValuesTest {

    @Test
    @DisplayName("应包含 ROR 定义的 9 种机构类型")
    void shouldContainAllRorTypes() {
      assertThat(OrganizationType.values()).hasSize(9);
      assertThat(OrganizationType.values())
          .extracting(OrganizationType::name)
          .containsExactlyInAnyOrder(
              "ARCHIVE",
              "COMPANY",
              "EDUCATION",
              "FACILITY",
              "FUNDER",
              "GOVERNMENT",
              "HEALTHCARE",
              "NONPROFIT",
              "OTHER");
    }

    @ParameterizedTest
    @CsvSource({
      "ARCHIVE, archive, 档案馆",
      "COMPANY, company, 企业",
      "EDUCATION, education, 教育机构",
      "FACILITY, facility, 设施",
      "FUNDER, funder, 资助机构",
      "GOVERNMENT, government, 政府机构",
      "HEALTHCARE, healthcare, 医疗机构",
      "NONPROFIT, nonprofit, 非营利组织",
      "OTHER, other, 其他"
    })
    @DisplayName("每个枚举值应有正确的 code 和 description")
    void shouldHaveCorrectCodeAndDescription(
        String enumName, String expectedCode, String expectedDescription) {
      OrganizationType type = OrganizationType.valueOf(enumName);
      assertThat(type.getCode()).isEqualTo(expectedCode);
      assertThat(type.getDescription()).isEqualTo(expectedDescription);
    }
  }

  @Nested
  @DisplayName("fromCode() 方法测试")
  class FromCodeTest {

    @ParameterizedTest
    @CsvSource({
      "archive, ARCHIVE",
      "ARCHIVE, ARCHIVE",
      "Archive, ARCHIVE",
      "education, EDUCATION",
      "healthcare, HEALTHCARE",
      "funder, FUNDER"
    })
    @DisplayName("应支持大小写不敏感的代码解析")
    void shouldParseCodeCaseInsensitively(String code, String expectedEnum) {
      assertThat(OrganizationType.fromCode(code).name()).isEqualTo(expectedEnum);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    @DisplayName("空白值应抛出 IllegalArgumentException")
    void shouldThrowExceptionForBlankValue(String code) {
      assertThatThrownBy(() -> OrganizationType.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知代码应抛出 IllegalArgumentException")
    void shouldThrowExceptionForUnknownCode() {
      assertThatThrownBy(() -> OrganizationType.fromCode("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的机构类型");
    }
  }

  @Nested
  @DisplayName("fromCodeOrNull() 方法测试")
  class FromCodeOrNullTest {

    @Test
    @DisplayName("有效代码应返回对应枚举")
    void shouldReturnEnumForValidCode() {
      assertThat(OrganizationType.fromCodeOrNull("education"))
          .isEqualTo(OrganizationType.EDUCATION);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "unknown", "invalid"})
    @DisplayName("无效或空白值应返回 null")
    void shouldReturnNullForInvalidOrBlankValue(String code) {
      assertThat(OrganizationType.fromCodeOrNull(code)).isNull();
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("isEducation() 应正确识别教育机构")
    void shouldIdentifyEducation() {
      assertThat(OrganizationType.EDUCATION.isEducation()).isTrue();
      assertThat(OrganizationType.HEALTHCARE.isEducation()).isFalse();
    }

    @Test
    @DisplayName("isHealthcare() 应正确识别医疗机构")
    void shouldIdentifyHealthcare() {
      assertThat(OrganizationType.HEALTHCARE.isHealthcare()).isTrue();
      assertThat(OrganizationType.EDUCATION.isHealthcare()).isFalse();
    }

    @Test
    @DisplayName("isCompany() 应正确识别企业")
    void shouldIdentifyCompany() {
      assertThat(OrganizationType.COMPANY.isCompany()).isTrue();
      assertThat(OrganizationType.NONPROFIT.isCompany()).isFalse();
    }

    @Test
    @DisplayName("isFunder() 应正确识别资助机构")
    void shouldIdentifyFunder() {
      assertThat(OrganizationType.FUNDER.isFunder()).isTrue();
      assertThat(OrganizationType.COMPANY.isFunder()).isFalse();
    }

    @Test
    @DisplayName("isResearchRelated() 应正确识别研究相关机构")
    void shouldIdentifyResearchRelated() {
      // 教育、医疗、设施、资助机构通常与研究相关
      assertThat(OrganizationType.EDUCATION.isResearchRelated()).isTrue();
      assertThat(OrganizationType.HEALTHCARE.isResearchRelated()).isTrue();
      assertThat(OrganizationType.FACILITY.isResearchRelated()).isTrue();
      assertThat(OrganizationType.FUNDER.isResearchRelated()).isTrue();
      // 企业、档案馆、政府、非营利、其他不一定
      assertThat(OrganizationType.COMPANY.isResearchRelated()).isFalse();
      assertThat(OrganizationType.ARCHIVE.isResearchRelated()).isFalse();
    }
  }
}
