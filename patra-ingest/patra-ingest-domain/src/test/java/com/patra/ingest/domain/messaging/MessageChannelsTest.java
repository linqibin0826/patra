package com.patra.ingest.domain.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MessageChannels 业务消息通道常量测试")
class MessageChannelsTest {

  @Nested
  @DisplayName("常量值验证")
  class ConstantValues {

    @Test
    @DisplayName("TASK_READY 常量应该有正确的值")
    void taskReadyShouldHaveCorrectValue() {
      // When
      String value = MessageChannels.TASK_READY;

      // Then
      assertThat(value).isEqualTo("TASK_READY");
    }

    @Test
    @DisplayName("LITERATURE_READY 常量应该有正确的值")
    void literatureReadyShouldHaveCorrectValue() {
      // When
      String value = MessageChannels.LITERATURE_READY;

      // Then
      assertThat(value).isEqualTo("LITERATURE_READY");
    }
  }

  @Nested
  @DisplayName("常量不变性")
  class ConstantImmutability {

    @Test
    @DisplayName("TASK_READY 应该是非空字符串")
    void taskReadyShouldNotBeEmpty() {
      // Then
      assertThat(MessageChannels.TASK_READY)
          .isNotNull()
          .isNotEmpty()
          .isNotBlank();
    }

    @Test
    @DisplayName("LITERATURE_READY 应该是非空字符串")
    void literatureReadyShouldNotBeEmpty() {
      // Then
      assertThat(MessageChannels.LITERATURE_READY)
          .isNotNull()
          .isNotEmpty()
          .isNotBlank();
    }

    @Test
    @DisplayName("两个常量应该不相同")
    void constantsShouldBeDifferent() {
      // Then
      assertThat(MessageChannels.TASK_READY)
          .isNotEqualTo(MessageChannels.LITERATURE_READY);
    }
  }

  @Nested
  @DisplayName("命名规范验证")
  class NamingConventions {

    @Test
    @DisplayName("TASK_READY 应该使用大写蛇形命名")
    void taskReadyShouldUseUpperSnakeCase() {
      // When
      String value = MessageChannels.TASK_READY;

      // Then
      assertThat(value).matches("^[A-Z_]+$");
    }

    @Test
    @DisplayName("LITERATURE_READY 应该使用大写蛇形命名")
    void literatureReadyShouldUseUpperSnakeCase() {
      // When
      String value = MessageChannels.LITERATURE_READY;

      // Then
      assertThat(value).matches("^[A-Z_]+$");
    }

    @Test
    @DisplayName("常量名称应该表达业务意图")
    void constantsShouldExpressBusinessIntent() {
      // Then
      assertThat(MessageChannels.TASK_READY)
          .contains("TASK")
          .contains("READY");

      assertThat(MessageChannels.LITERATURE_READY)
          .contains("LITERATURE")
          .contains("READY");
    }
  }

  @Nested
  @DisplayName("实例化测试")
  class InstantiationTest {

    @Test
    @DisplayName("应该禁止实例化常量类")
    void shouldPreventInstantiation() {
      // When & Then
      assertThatThrownBy(() -> {
        // 使用反射尝试调用私有构造器
        var constructor = MessageChannels.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
      })
          .hasCauseInstanceOf(UnsupportedOperationException.class)
          .hasStackTraceContaining("常量类不允许实例化");
    }
  }

  @Nested
  @DisplayName("业务语义验证")
  class BusinessSemantics {

    @Test
    @DisplayName("TASK_READY 应该表示任务就绪事件")
    void taskReadyShouldRepresentTaskReadyEvent() {
      // Given
      String channelName = MessageChannels.TASK_READY;

      // Then
      assertThat(channelName)
          .as("任务就绪通道应该明确表达任务已准备好执行的语义")
          .isEqualTo("TASK_READY");
    }

    @Test
    @DisplayName("LITERATURE_READY 应该表示文献数据就绪事件")
    void literatureReadyShouldRepresentLiteratureReadyEvent() {
      // Given
      String channelName = MessageChannels.LITERATURE_READY;

      // Then
      assertThat(channelName)
          .as("文献就绪通道应该明确表达文献数据已准备好的语义")
          .isEqualTo("LITERATURE_READY");
    }
  }

  @Nested
  @DisplayName("常量引用一致性")
  class ConstantReferenceConsistency {

    @Test
    @DisplayName("多次访问 TASK_READY 应该返回相同引用")
    void taskReadyShouldReturnSameReference() {
      // When
      String ref1 = MessageChannels.TASK_READY;
      String ref2 = MessageChannels.TASK_READY;

      // Then
      assertThat(ref1).isSameAs(ref2);
    }

    @Test
    @DisplayName("多次访问 LITERATURE_READY 应该返回相同引用")
    void literatureReadyShouldReturnSameReference() {
      // When
      String ref1 = MessageChannels.LITERATURE_READY;
      String ref2 = MessageChannels.LITERATURE_READY;

      // Then
      assertThat(ref1).isSameAs(ref2);
    }
  }
}
