package dev.linqibin.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// AuthorStatus 枚举单元测试。
///
/// 测试范围：
///
/// - 代码解析（fromCode、fromCodeOrNull）
/// - 状态判断（isActive、isMerged、isInactive）
/// - 业务判断（isEditable、isVisible、isTerminal）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("AuthorStatus 枚举测试")
class AuthorStatusTest {

  @Nested
  @DisplayName("fromCode 解析测试")
  class FromCodeTests {

    @Test
    @DisplayName("解析 ACTIVE 代码")
    void shouldParseActiveCode() {
      assertThat(AuthorStatus.fromCode("ACTIVE")).isEqualTo(AuthorStatus.ACTIVE);
    }

    @Test
    @DisplayName("解析 MERGED 代码")
    void shouldParseMergedCode() {
      assertThat(AuthorStatus.fromCode("MERGED")).isEqualTo(AuthorStatus.MERGED);
    }

    @Test
    @DisplayName("解析 INACTIVE 代码")
    void shouldParseInactiveCode() {
      assertThat(AuthorStatus.fromCode("INACTIVE")).isEqualTo(AuthorStatus.INACTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "Active", "ACTIVE", " ACTIVE ", "active "})
    @DisplayName("解析应不区分大小写并忽略空格")
    void shouldParseCaseInsensitiveWithTrim(String input) {
      assertThat(AuthorStatus.fromCode(input)).isEqualTo(AuthorStatus.ACTIVE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("空白值应抛出 IllegalArgumentException")
    void shouldThrowOnBlankValue(String input) {
      assertThatThrownBy(() -> AuthorStatus.fromCode(input))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知代码应抛出 IllegalArgumentException")
    void shouldThrowOnUnknownCode() {
      assertThatThrownBy(() -> AuthorStatus.fromCode("UNKNOWN"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的作者状态");
    }
  }

  @Nested
  @DisplayName("fromCodeOrNull 安全解析测试")
  class FromCodeOrNullTests {

    @Test
    @DisplayName("解析有效代码应返回枚举值")
    void shouldReturnEnumForValidCode() {
      assertThat(AuthorStatus.fromCodeOrNull("ACTIVE")).isEqualTo(AuthorStatus.ACTIVE);
      assertThat(AuthorStatus.fromCodeOrNull("MERGED")).isEqualTo(AuthorStatus.MERGED);
      assertThat(AuthorStatus.fromCodeOrNull("INACTIVE")).isEqualTo(AuthorStatus.INACTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "merged", "inactive"})
    @DisplayName("解析应不区分大小写")
    void shouldParseCaseInsensitive(String input) {
      assertThat(AuthorStatus.fromCodeOrNull(input)).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("空白值应返回 null")
    void shouldReturnNullForBlankValue(String input) {
      assertThat(AuthorStatus.fromCodeOrNull(input)).isNull();
    }

    @Test
    @DisplayName("未知代码应返回 null")
    void shouldReturnNullForUnknownCode() {
      assertThat(AuthorStatus.fromCodeOrNull("UNKNOWN")).isNull();
      assertThat(AuthorStatus.fromCodeOrNull("DELETED")).isNull();
    }
  }

  @Nested
  @DisplayName("状态判断方法测试")
  class StatusCheckTests {

    @Test
    @DisplayName("ACTIVE 状态判断")
    void shouldIdentifyActiveStatus() {
      AuthorStatus status = AuthorStatus.ACTIVE;

      assertThat(status.isActive()).isTrue();
      assertThat(status.isMerged()).isFalse();
      assertThat(status.isInactive()).isFalse();
    }

    @Test
    @DisplayName("MERGED 状态判断")
    void shouldIdentifyMergedStatus() {
      AuthorStatus status = AuthorStatus.MERGED;

      assertThat(status.isActive()).isFalse();
      assertThat(status.isMerged()).isTrue();
      assertThat(status.isInactive()).isFalse();
    }

    @Test
    @DisplayName("INACTIVE 状态判断")
    void shouldIdentifyInactiveStatus() {
      AuthorStatus status = AuthorStatus.INACTIVE;

      assertThat(status.isActive()).isFalse();
      assertThat(status.isMerged()).isFalse();
      assertThat(status.isInactive()).isTrue();
    }
  }

  @Nested
  @DisplayName("业务判断方法测试")
  class BusinessCheckTests {

    @Test
    @DisplayName("仅 ACTIVE 状态可编辑")
    void shouldBeEditableOnlyWhenActive() {
      assertThat(AuthorStatus.ACTIVE.isEditable()).isTrue();
      assertThat(AuthorStatus.MERGED.isEditable()).isFalse();
      assertThat(AuthorStatus.INACTIVE.isEditable()).isFalse();
    }

    @Test
    @DisplayName("ACTIVE 和 MERGED 状态可见")
    void shouldBeVisibleWhenActiveOrMerged() {
      assertThat(AuthorStatus.ACTIVE.isVisible()).isTrue();
      assertThat(AuthorStatus.MERGED.isVisible()).isTrue();
      assertThat(AuthorStatus.INACTIVE.isVisible()).isFalse();
    }

    @Test
    @DisplayName("MERGED 和 INACTIVE 为终态")
    void shouldBeTerminalWhenMergedOrInactive() {
      assertThat(AuthorStatus.ACTIVE.isTerminal()).isFalse();
      assertThat(AuthorStatus.MERGED.isTerminal()).isTrue();
      assertThat(AuthorStatus.INACTIVE.isTerminal()).isTrue();
    }
  }

  @Nested
  @DisplayName("属性访问测试")
  class PropertyAccessTests {

    @Test
    @DisplayName("获取代码值")
    void shouldReturnCorrectCode() {
      assertThat(AuthorStatus.ACTIVE.getCode()).isEqualTo("ACTIVE");
      assertThat(AuthorStatus.MERGED.getCode()).isEqualTo("MERGED");
      assertThat(AuthorStatus.INACTIVE.getCode()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("获取描述")
    void shouldReturnCorrectDescription() {
      assertThat(AuthorStatus.ACTIVE.getDescription()).isEqualTo("活跃");
      assertThat(AuthorStatus.MERGED.getDescription()).isEqualTo("已合并");
      assertThat(AuthorStatus.INACTIVE.getDescription()).isEqualTo("已停用");
    }
  }
}
