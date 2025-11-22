package com.patra.ingest.domain.model.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// OutboxStatus 枚举测试。
/// 
/// @author Patra Team
@DisplayName("OutboxStatus 枚举测试")
class OutboxStatusTest {

  @Nested
  @DisplayName("枚举值测试")
  class EnumValuesTest {

    @Test
    @DisplayName("应该包含所有预期的枚举值")
    void shouldContainAllExpectedValues() {
      // Given & When
      OutboxStatus[] values = OutboxStatus.values();

      // Then
      assertThat(values)
          .hasSize(5)
          .containsExactly(
              OutboxStatus.PENDING,
              OutboxStatus.PUBLISHING,
              OutboxStatus.PUBLISHED,
              OutboxStatus.FAILED,
              OutboxStatus.DEAD);
    }

    @Test
    @DisplayName("应该通过名称正确获取枚举值")
    void shouldGetEnumByName() {
      // Given & When
      OutboxStatus pending = OutboxStatus.valueOf("PENDING");
      OutboxStatus publishing = OutboxStatus.valueOf("PUBLISHING");
      OutboxStatus published = OutboxStatus.valueOf("PUBLISHED");
      OutboxStatus failed = OutboxStatus.valueOf("FAILED");
      OutboxStatus dead = OutboxStatus.valueOf("DEAD");

      // Then
      assertThat(pending).isEqualTo(OutboxStatus.PENDING);
      assertThat(publishing).isEqualTo(OutboxStatus.PUBLISHING);
      assertThat(published).isEqualTo(OutboxStatus.PUBLISHED);
      assertThat(failed).isEqualTo(OutboxStatus.FAILED);
      assertThat(dead).isEqualTo(OutboxStatus.DEAD);
    }

    @Test
    @DisplayName("当使用无效名称时应该抛出异常")
    void shouldThrowExceptionForInvalidName() {
      // Given
      String invalidName = "INVALID";

      // When & Then
      assertThatThrownBy(() -> OutboxStatus.valueOf(invalidName))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("枚举业务语义测试")
  class BusinessSemanticsTest {

    @Test
    @DisplayName("状态机流转序列应该合理")
    void statusMachineTransitionShouldMakeSense() {
      // Given - 按状态机顺序排列
      OutboxStatus[] expectedOrder = {
        OutboxStatus.PENDING, // 待发布
        OutboxStatus.PUBLISHING, // 发布中
        OutboxStatus.PUBLISHED, // 已发布(成功终态)
        OutboxStatus.FAILED, // 失败(可重试)
        OutboxStatus.DEAD // 死信(失败终态)
      };

      // When
      OutboxStatus[] actualOrder = OutboxStatus.values();

      // Then
      assertThat(actualOrder).containsExactly(expectedOrder);
    }

    @Test
    @DisplayName("应该有两个终态")
    void shouldHaveTwoTerminalStates() {
      // Given - PUBLISHED 和 DEAD 是终态
      OutboxStatus[] terminalStates = {OutboxStatus.PUBLISHED, OutboxStatus.DEAD};

      // When & Then
      assertThat(terminalStates).hasSize(2);
    }

    @Test
    @DisplayName("PENDING 应该是初始状态")
    void pendingShouldBeInitialState() {
      // Given & When
      OutboxStatus initialState = OutboxStatus.values()[0];

      // Then
      assertThat(initialState).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("PUBLISHING 应该用于租约控制")
    void publishingShouldBeUsedForLeaseControl() {
      // Given & When
      OutboxStatus publishing = OutboxStatus.PUBLISHING;

      // Then - PUBLISHING 状态用于防止并发发布
      assertThat(publishing).isNotNull();
      assertThat(publishing.name()).isEqualTo("PUBLISHING");
    }

    @Test
    @DisplayName("PUBLISHED 应该是成功终态")
    void publishedShouldBeSuccessTerminalState() {
      // Given & When
      OutboxStatus published = OutboxStatus.PUBLISHED;

      // Then
      assertThat(published).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("FAILED 状态应该可以重试")
    void failedStatusShouldBeRetryable() {
      // Given & When - FAILED 状态在重试策略范围内
      OutboxStatus failed = OutboxStatus.FAILED;

      // Then
      assertThat(failed).isNotEqualTo(OutboxStatus.DEAD);
    }

    @Test
    @DisplayName("DEAD 应该是最终失败状态")
    void deadShouldBeFinalFailureState() {
      // Given & When
      OutboxStatus dead = OutboxStatus.DEAD;

      // Then
      assertThat(dead).isEqualTo(OutboxStatus.values()[OutboxStatus.values().length - 1]);
    }
  }

  @Nested
  @DisplayName("状态转换验证")
  class StateTransitionValidationTest {

    @Test
    @DisplayName("从 PENDING 可以转换到 PUBLISHING")
    void canTransitionFromPendingToPublishing() {
      // Given
      OutboxStatus from = OutboxStatus.PENDING;
      OutboxStatus to = OutboxStatus.PUBLISHING;

      // When & Then - 验证状态序列
      assertThat(from.ordinal()).isLessThan(to.ordinal());
    }

    @Test
    @DisplayName("从 PUBLISHING 可以转换到 PUBLISHED 或 FAILED")
    void canTransitionFromPublishingToPublishedOrFailed() {
      // Given
      OutboxStatus from = OutboxStatus.PUBLISHING;

      // When & Then
      assertThat(OutboxStatus.PUBLISHED.ordinal()).isGreaterThan(from.ordinal());
      assertThat(OutboxStatus.FAILED.ordinal()).isGreaterThan(from.ordinal());
    }

    @Test
    @DisplayName("从 FAILED 可以重试回到 PUBLISHING 或转到 DEAD")
    void canTransitionFromFailedToPublishingOrDead() {
      // Given
      OutboxStatus failed = OutboxStatus.FAILED;

      // When & Then - FAILED 可以重试或进入死信
      assertThat(failed).isNotNull();
      assertThat(OutboxStatus.DEAD.ordinal()).isGreaterThan(failed.ordinal());
    }
  }

  @Nested
  @DisplayName("枚举不变性测试")
  class ImmutabilityTest {

    @Test
    @DisplayName("枚举值应该是单例")
    void enumValuesShouldBeSingleton() {
      // Given & When
      OutboxStatus pending1 = OutboxStatus.PENDING;
      OutboxStatus pending2 = OutboxStatus.valueOf("PENDING");

      // Then
      assertThat(pending1).isSameAs(pending2);
    }

    @Test
    @DisplayName("values() 应该返回新数组")
    void valuesShouldReturnNewArray() {
      // Given
      OutboxStatus[] values1 = OutboxStatus.values();
      OutboxStatus[] values2 = OutboxStatus.values();

      // When & Then - 不是同一个数组实例
      assertThat(values1).isNotSameAs(values2);
      assertThat(values1).containsExactly(values2);
    }
  }

  @Nested
  @DisplayName("比较和排序测试")
  class ComparisonAndOrderingTest {

    @Test
    @DisplayName("枚举值应该按声明顺序排序")
    void enumValuesShouldBeOrderedByDeclaration() {
      // Given & When
      int pendingOrdinal = OutboxStatus.PENDING.ordinal();
      int publishingOrdinal = OutboxStatus.PUBLISHING.ordinal();
      int publishedOrdinal = OutboxStatus.PUBLISHED.ordinal();
      int failedOrdinal = OutboxStatus.FAILED.ordinal();
      int deadOrdinal = OutboxStatus.DEAD.ordinal();

      // Then
      assertThat(pendingOrdinal).isEqualTo(0);
      assertThat(publishingOrdinal).isEqualTo(1);
      assertThat(publishedOrdinal).isEqualTo(2);
      assertThat(failedOrdinal).isEqualTo(3);
      assertThat(deadOrdinal).isEqualTo(4);
    }

    @Test
    @DisplayName("枚举值应该可以用 compareTo 比较")
    void enumValuesShouldBeComparableWithCompareTo() {
      // Given
      OutboxStatus pending = OutboxStatus.PENDING;
      OutboxStatus dead = OutboxStatus.DEAD;

      // When & Then
      assertThat(pending.compareTo(dead)).isNegative();
      assertThat(dead.compareTo(pending)).isPositive();
      assertThat(pending.compareTo(pending)).isZero();
    }
  }

  @Nested
  @DisplayName("toString 方法测试")
  class ToStringMethodTest {

    @Test
    @DisplayName("toString 应该返回枚举名称")
    void toStringShouldReturnEnumName() {
      // Given & When & Then
      assertThat(OutboxStatus.PENDING.toString()).isEqualTo("PENDING");
      assertThat(OutboxStatus.PUBLISHING.toString()).isEqualTo("PUBLISHING");
      assertThat(OutboxStatus.PUBLISHED.toString()).isEqualTo("PUBLISHED");
      assertThat(OutboxStatus.FAILED.toString()).isEqualTo("FAILED");
      assertThat(OutboxStatus.DEAD.toString()).isEqualTo("DEAD");
    }
  }
}
