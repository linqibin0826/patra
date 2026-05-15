package dev.linqibin.patra.catalog.domain.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// DisambiguationStatus 枚举单元测试。
///
/// 测试范围：
///
/// - 代码解析（fromCode、fromCodeOrNull）
/// - 状态判断（isPending、isMatched、isUnmatched、isAmbiguous）
/// - 业务判断（needsManualReview、isTerminal）
/// - 属性访问（getCode、getDescription）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("DisambiguationStatus 枚举测试")
@Timeout(2)
class DisambiguationStatusTest {

  @Nested
  @DisplayName("fromCode 解析测试")
  class FromCodeTests {

    @Test
    @DisplayName("解析 PENDING 代码")
    void shouldParsePendingCode() {
      assertThat(DisambiguationStatus.fromCode("PENDING")).isEqualTo(DisambiguationStatus.PENDING);
    }

    @Test
    @DisplayName("解析 MATCHED 代码")
    void shouldParseMatchedCode() {
      assertThat(DisambiguationStatus.fromCode("MATCHED")).isEqualTo(DisambiguationStatus.MATCHED);
    }

    @Test
    @DisplayName("解析 UNMATCHED 代码")
    void shouldParseUnmatchedCode() {
      assertThat(DisambiguationStatus.fromCode("UNMATCHED"))
          .isEqualTo(DisambiguationStatus.UNMATCHED);
    }

    @Test
    @DisplayName("解析 AMBIGUOUS 代码")
    void shouldParseAmbiguousCode() {
      assertThat(DisambiguationStatus.fromCode("AMBIGUOUS"))
          .isEqualTo(DisambiguationStatus.AMBIGUOUS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "Pending", "PENDING", " PENDING ", "pending "})
    @DisplayName("解析应不区分大小写并忽略空格")
    void shouldParseCaseInsensitiveWithTrim(String input) {
      assertThat(DisambiguationStatus.fromCode(input)).isEqualTo(DisambiguationStatus.PENDING);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("空白值应抛出 IllegalArgumentException")
    void shouldThrowOnBlankValue(String input) {
      assertThatThrownBy(() -> DisambiguationStatus.fromCode(input))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知代码应抛出 IllegalArgumentException")
    void shouldThrowOnUnknownCode() {
      assertThatThrownBy(() -> DisambiguationStatus.fromCode("UNKNOWN"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的消歧状态");
    }
  }

  @Nested
  @DisplayName("fromCodeOrNull 安全解析测试")
  class FromCodeOrNullTests {

    @Test
    @DisplayName("解析有效代码应返回枚举值")
    void shouldReturnEnumForValidCode() {
      assertThat(DisambiguationStatus.fromCodeOrNull("PENDING"))
          .isEqualTo(DisambiguationStatus.PENDING);
      assertThat(DisambiguationStatus.fromCodeOrNull("MATCHED"))
          .isEqualTo(DisambiguationStatus.MATCHED);
      assertThat(DisambiguationStatus.fromCodeOrNull("UNMATCHED"))
          .isEqualTo(DisambiguationStatus.UNMATCHED);
      assertThat(DisambiguationStatus.fromCodeOrNull("AMBIGUOUS"))
          .isEqualTo(DisambiguationStatus.AMBIGUOUS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "matched", "unmatched", "ambiguous"})
    @DisplayName("解析应不区分大小写")
    void shouldParseCaseInsensitive(String input) {
      assertThat(DisambiguationStatus.fromCodeOrNull(input)).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("空白值应返回 null")
    void shouldReturnNullForBlankValue(String input) {
      assertThat(DisambiguationStatus.fromCodeOrNull(input)).isNull();
    }

    @Test
    @DisplayName("未知代码应返回 null")
    void shouldReturnNullForUnknownCode() {
      assertThat(DisambiguationStatus.fromCodeOrNull("UNKNOWN")).isNull();
      assertThat(DisambiguationStatus.fromCodeOrNull("DELETED")).isNull();
    }
  }

  @Nested
  @DisplayName("状态判断方法测试")
  class StatusCheckTests {

    @Test
    @DisplayName("PENDING 状态判断")
    void shouldIdentifyPendingStatus() {
      DisambiguationStatus status = DisambiguationStatus.PENDING;

      assertThat(status.isPending()).isTrue();
      assertThat(status.isMatched()).isFalse();
      assertThat(status.isUnmatched()).isFalse();
      assertThat(status.isAmbiguous()).isFalse();
    }

    @Test
    @DisplayName("MATCHED 状态判断")
    void shouldIdentifyMatchedStatus() {
      DisambiguationStatus status = DisambiguationStatus.MATCHED;

      assertThat(status.isPending()).isFalse();
      assertThat(status.isMatched()).isTrue();
      assertThat(status.isUnmatched()).isFalse();
      assertThat(status.isAmbiguous()).isFalse();
    }

    @Test
    @DisplayName("UNMATCHED 状态判断")
    void shouldIdentifyUnmatchedStatus() {
      DisambiguationStatus status = DisambiguationStatus.UNMATCHED;

      assertThat(status.isPending()).isFalse();
      assertThat(status.isMatched()).isFalse();
      assertThat(status.isUnmatched()).isTrue();
      assertThat(status.isAmbiguous()).isFalse();
    }

    @Test
    @DisplayName("AMBIGUOUS 状态判断")
    void shouldIdentifyAmbiguousStatus() {
      DisambiguationStatus status = DisambiguationStatus.AMBIGUOUS;

      assertThat(status.isPending()).isFalse();
      assertThat(status.isMatched()).isFalse();
      assertThat(status.isUnmatched()).isFalse();
      assertThat(status.isAmbiguous()).isTrue();
    }
  }

  @Nested
  @DisplayName("业务判断方法测试")
  class BusinessCheckTests {

    @Test
    @DisplayName("UNMATCHED 和 AMBIGUOUS 需要人工处理")
    void shouldNeedManualReviewForUnmatchedAndAmbiguous() {
      assertThat(DisambiguationStatus.UNMATCHED.needsManualReview()).isTrue();
      assertThat(DisambiguationStatus.AMBIGUOUS.needsManualReview()).isTrue();
    }

    @Test
    @DisplayName("PENDING 和 MATCHED 不需要人工处理")
    void shouldNotNeedManualReviewForPendingAndMatched() {
      assertThat(DisambiguationStatus.PENDING.needsManualReview()).isFalse();
      assertThat(DisambiguationStatus.MATCHED.needsManualReview()).isFalse();
    }

    @Test
    @DisplayName("MATCHED 和 UNMATCHED 为终态")
    void shouldBeTerminalWhenMatchedOrUnmatched() {
      assertThat(DisambiguationStatus.MATCHED.isTerminal()).isTrue();
      assertThat(DisambiguationStatus.UNMATCHED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("PENDING 和 AMBIGUOUS 不是终态")
    void shouldNotBeTerminalWhenPendingOrAmbiguous() {
      assertThat(DisambiguationStatus.PENDING.isTerminal()).isFalse();
      assertThat(DisambiguationStatus.AMBIGUOUS.isTerminal()).isFalse();
    }
  }

  @Nested
  @DisplayName("属性访问测试")
  class PropertyAccessTests {

    @Test
    @DisplayName("获取代码值")
    void shouldReturnCorrectCode() {
      assertThat(DisambiguationStatus.PENDING.getCode()).isEqualTo("PENDING");
      assertThat(DisambiguationStatus.MATCHED.getCode()).isEqualTo("MATCHED");
      assertThat(DisambiguationStatus.UNMATCHED.getCode()).isEqualTo("UNMATCHED");
      assertThat(DisambiguationStatus.AMBIGUOUS.getCode()).isEqualTo("AMBIGUOUS");
    }

    @Test
    @DisplayName("获取描述")
    void shouldReturnCorrectDescription() {
      assertThat(DisambiguationStatus.PENDING.getDescription()).isEqualTo("待消歧");
      assertThat(DisambiguationStatus.MATCHED.getDescription()).isEqualTo("已匹配");
      assertThat(DisambiguationStatus.UNMATCHED.getDescription()).isEqualTo("无法匹配");
      assertThat(DisambiguationStatus.AMBIGUOUS.getDescription()).isEqualTo("有歧义");
    }
  }

  @Nested
  @DisplayName("状态转换场景测试")
  class StateTransitionTests {

    @Test
    @DisplayName("PENDING 可以转换到所有其他状态")
    void pendingCanTransitionToAllStates() {
      // PENDING 是初始状态，可以转换到任意终态
      DisambiguationStatus pending = DisambiguationStatus.PENDING;

      assertThat(pending.isPending()).isTrue();
      assertThat(pending.isTerminal()).isFalse();

      // 以下状态都可以从 PENDING 转换得到
      assertThat(DisambiguationStatus.MATCHED.isTerminal()).isTrue();
      assertThat(DisambiguationStatus.UNMATCHED.isTerminal()).isTrue();
      assertThat(DisambiguationStatus.AMBIGUOUS.needsManualReview()).isTrue();
    }

    @Test
    @DisplayName("AMBIGUOUS 需要人工处理后可以转换到终态")
    void ambiguousRequiresManualReviewBeforeTerminal() {
      DisambiguationStatus ambiguous = DisambiguationStatus.AMBIGUOUS;

      assertThat(ambiguous.needsManualReview()).isTrue();
      assertThat(ambiguous.isTerminal()).isFalse();
    }
  }
}
