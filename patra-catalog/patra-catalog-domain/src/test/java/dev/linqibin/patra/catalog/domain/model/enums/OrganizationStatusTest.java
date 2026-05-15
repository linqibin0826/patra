package dev.linqibin.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// OrganizationStatus 枚举测试。
///
/// 基于 ROR Schema v2.0 的机构状态定义：active, inactive, withdrawn
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("OrganizationStatus 枚举测试")
class OrganizationStatusTest {

  @Nested
  @DisplayName("枚举值验证")
  class EnumValuesTest {

    @Test
    @DisplayName("应包含 ROR 定义的 3 种状态")
    void shouldContainAllRorStatuses() {
      assertThat(OrganizationStatus.values()).hasSize(3);
      assertThat(OrganizationStatus.values())
          .extracting(OrganizationStatus::name)
          .containsExactlyInAnyOrder("ACTIVE", "INACTIVE", "WITHDRAWN");
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE, active, 活跃", "INACTIVE, inactive, 不活跃", "WITHDRAWN, withdrawn, 已撤回"})
    @DisplayName("每个枚举值应有正确的 code 和 description")
    void shouldHaveCorrectCodeAndDescription(
        String enumName, String expectedCode, String expectedDescription) {
      OrganizationStatus status = OrganizationStatus.valueOf(enumName);
      assertThat(status.getCode()).isEqualTo(expectedCode);
      assertThat(status.getDescription()).isEqualTo(expectedDescription);
    }
  }

  @Nested
  @DisplayName("fromCode() 方法测试")
  class FromCodeTest {

    @ParameterizedTest
    @CsvSource({
      "active, ACTIVE",
      "ACTIVE, ACTIVE",
      "Active, ACTIVE",
      "inactive, INACTIVE",
      "withdrawn, WITHDRAWN"
    })
    @DisplayName("应支持大小写不敏感的代码解析")
    void shouldParseCodeCaseInsensitively(String code, String expectedEnum) {
      assertThat(OrganizationStatus.fromCode(code).name()).isEqualTo(expectedEnum);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    @DisplayName("空白值应抛出 IllegalArgumentException")
    void shouldThrowExceptionForBlankValue(String code) {
      assertThatThrownBy(() -> OrganizationStatus.fromCode(code))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知代码应抛出 IllegalArgumentException")
    void shouldThrowExceptionForUnknownCode() {
      assertThatThrownBy(() -> OrganizationStatus.fromCode("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的机构状态");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("isActive() 应正确识别活跃状态")
    void shouldIdentifyActive() {
      assertThat(OrganizationStatus.ACTIVE.isActive()).isTrue();
      assertThat(OrganizationStatus.INACTIVE.isActive()).isFalse();
      assertThat(OrganizationStatus.WITHDRAWN.isActive()).isFalse();
    }

    @Test
    @DisplayName("isInactive() 应正确识别不活跃状态")
    void shouldIdentifyInactive() {
      assertThat(OrganizationStatus.INACTIVE.isInactive()).isTrue();
      assertThat(OrganizationStatus.ACTIVE.isInactive()).isFalse();
    }

    @Test
    @DisplayName("isWithdrawn() 应正确识别已撤回状态")
    void shouldIdentifyWithdrawn() {
      assertThat(OrganizationStatus.WITHDRAWN.isWithdrawn()).isTrue();
      assertThat(OrganizationStatus.ACTIVE.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("isAvailable() 应正确识别可用状态（非撤回）")
    void shouldIdentifyAvailable() {
      assertThat(OrganizationStatus.ACTIVE.isAvailable()).isTrue();
      assertThat(OrganizationStatus.INACTIVE.isAvailable()).isTrue();
      assertThat(OrganizationStatus.WITHDRAWN.isAvailable()).isFalse();
    }
  }
}
