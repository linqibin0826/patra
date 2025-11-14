package com.patra.ingest.domain.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MessageOperations 业务消息操作类型常量测试")
class MessageOperationsTest {

  @Nested
  @DisplayName("常量值验证")
  class ConstantValues {

    @Test
    @DisplayName("TASK_READY 常量应该有正确的值")
    void taskReadyShouldHaveCorrectValue() {
      // When
      String value = MessageOperations.TASK_READY;

      // Then
      assertThat(value).isEqualTo("INGEST_TASK_READY");
    }

    @Test
    @DisplayName("TASK_COMPLETED 常量应该有正确的值")
    void taskCompletedShouldHaveCorrectValue() {
      // When
      String value = MessageOperations.TASK_COMPLETED;

      // Then
      assertThat(value).isEqualTo("TASK_COMPLETED");
    }

    @Test
    @DisplayName("LITERATURE_READY 常量应该有正确的值")
    void literatureReadyShouldHaveCorrectValue() {
      // When
      String value = MessageOperations.LITERATURE_READY;

      // Then
      assertThat(value).isEqualTo("INGEST_LITERATURE_READY");
    }
  }

  @Nested
  @DisplayName("常量不变性")
  class ConstantImmutability {

    @Test
    @DisplayName("TASK_READY 应该是非空字符串")
    void taskReadyShouldNotBeEmpty() {
      // Then
      assertThat(MessageOperations.TASK_READY).isNotNull().isNotEmpty().isNotBlank();
    }

    @Test
    @DisplayName("TASK_COMPLETED 应该是非空字符串")
    void taskCompletedShouldNotBeEmpty() {
      // Then
      assertThat(MessageOperations.TASK_COMPLETED).isNotNull().isNotEmpty().isNotBlank();
    }

    @Test
    @DisplayName("LITERATURE_READY 应该是非空字符串")
    void literatureReadyShouldNotBeEmpty() {
      // Then
      assertThat(MessageOperations.LITERATURE_READY).isNotNull().isNotEmpty().isNotBlank();
    }

    @Test
    @DisplayName("所有常量应该互不相同")
    void allConstantsShouldBeDifferent() {
      // Then
      assertThat(MessageOperations.TASK_READY)
          .isNotEqualTo(MessageOperations.TASK_COMPLETED)
          .isNotEqualTo(MessageOperations.LITERATURE_READY);

      assertThat(MessageOperations.TASK_COMPLETED).isNotEqualTo(MessageOperations.LITERATURE_READY);
    }
  }

  @Nested
  @DisplayName("命名规范验证")
  class NamingConventions {

    @Test
    @DisplayName("TASK_READY 应该使用大写蛇形命名")
    void taskReadyShouldUseUpperSnakeCase() {
      // When
      String value = MessageOperations.TASK_READY;

      // Then
      assertThat(value).matches("^[A-Z_]+$");
    }

    @Test
    @DisplayName("TASK_COMPLETED 应该使用大写蛇形命名")
    void taskCompletedShouldUseUpperSnakeCase() {
      // When
      String value = MessageOperations.TASK_COMPLETED;

      // Then
      assertThat(value).matches("^[A-Z_]+$");
    }

    @Test
    @DisplayName("LITERATURE_READY 应该使用大写蛇形命名")
    void literatureReadyShouldUseUpperSnakeCase() {
      // When
      String value = MessageOperations.LITERATURE_READY;

      // Then
      assertThat(value).matches("^[A-Z_]+$");
    }

    @Test
    @DisplayName("常量名称应该表达业务操作意图")
    void constantsShouldExpressBusinessIntent() {
      // Then
      assertThat(MessageOperations.TASK_READY).contains("TASK").contains("READY");

      assertThat(MessageOperations.TASK_COMPLETED).contains("TASK").contains("COMPLETED");

      assertThat(MessageOperations.LITERATURE_READY).contains("LITERATURE").contains("READY");
    }
  }

  @Nested
  @DisplayName("实例化测试")
  class InstantiationTest {

    @Test
    @DisplayName("应该禁止实例化常量类")
    void shouldPreventInstantiation() {
      // When & Then
      assertThatThrownBy(
              () -> {
                // 使用反射尝试调用私有构造器
                var constructor = MessageOperations.class.getDeclaredConstructor();
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
    @DisplayName("TASK_READY 应该表示任务就绪操作")
    void taskReadyShouldRepresentTaskReadyOperation() {
      // Given
      String operation = MessageOperations.TASK_READY;

      // Then
      assertThat(operation).as("任务就绪操作应该明确表达任务已创建并等待执行的语义").isEqualTo("INGEST_TASK_READY");
    }

    @Test
    @DisplayName("TASK_COMPLETED 应该表示任务完成操作")
    void taskCompletedShouldRepresentTaskCompletedOperation() {
      // Given
      String operation = MessageOperations.TASK_COMPLETED;

      // Then
      assertThat(operation).as("任务完成操作应该明确表达任务已成功执行完毕的语义").isEqualTo("TASK_COMPLETED");
    }

    @Test
    @DisplayName("LITERATURE_READY 应该表示文献就绪操作")
    void literatureReadyShouldRepresentLiteratureReadyOperation() {
      // Given
      String operation = MessageOperations.LITERATURE_READY;

      // Then
      assertThat(operation).as("文献就绪操作应该明确表达文献数据已准备就绪的语义").isEqualTo("INGEST_LITERATURE_READY");
    }
  }

  @Nested
  @DisplayName("常量引用一致性")
  class ConstantReferenceConsistency {

    @Test
    @DisplayName("多次访问 TASK_READY 应该返回相同引用")
    void taskReadyShouldReturnSameReference() {
      // When
      String ref1 = MessageOperations.TASK_READY;
      String ref2 = MessageOperations.TASK_READY;

      // Then
      assertThat(ref1).isSameAs(ref2);
    }

    @Test
    @DisplayName("多次访问 TASK_COMPLETED 应该返回相同引用")
    void taskCompletedShouldReturnSameReference() {
      // When
      String ref1 = MessageOperations.TASK_COMPLETED;
      String ref2 = MessageOperations.TASK_COMPLETED;

      // Then
      assertThat(ref1).isSameAs(ref2);
    }

    @Test
    @DisplayName("多次访问 LITERATURE_READY 应该返回相同引用")
    void literatureReadyShouldReturnSameReference() {
      // When
      String ref1 = MessageOperations.LITERATURE_READY;
      String ref2 = MessageOperations.LITERATURE_READY;

      // Then
      assertThat(ref1).isSameAs(ref2);
    }
  }

  @Nested
  @DisplayName("操作类型分类")
  class OperationTypeClassification {

    @Test
    @DisplayName("任务相关操作应该包含 INGEST 前缀和 TASK 标识")
    void taskOperationsShouldContainIngestPrefixAndTaskIdentifier() {
      // Then
      assertThat(MessageOperations.TASK_READY).startsWith("INGEST").contains("TASK");
      assertThat(MessageOperations.TASK_COMPLETED).startsWith("TASK");
    }

    @Test
    @DisplayName("文献相关操作应该包含 INGEST 前缀和 LITERATURE 标识")
    void literatureOperationsShouldContainIngestPrefixAndLiteratureIdentifier() {
      // Then
      assertThat(MessageOperations.LITERATURE_READY).startsWith("INGEST").contains("LITERATURE");
    }

    @Test
    @DisplayName("就绪状态操作应该包含 READY 后缀")
    void readyOperationsShouldContainReadySuffix() {
      // Then
      assertThat(MessageOperations.TASK_READY).endsWith("READY");
      assertThat(MessageOperations.LITERATURE_READY).endsWith("READY");
    }

    @Test
    @DisplayName("完成状态操作应该包含 COMPLETED 后缀")
    void completedOperationsShouldContainCompletedSuffix() {
      // Then
      assertThat(MessageOperations.TASK_COMPLETED).endsWith("COMPLETED");
    }
  }

  @Nested
  @DisplayName("与其他 Messaging 常量的对比")
  class ComparisonWithOtherMessagingConstants {

    @Test
    @DisplayName("TASK_READY 在 MessageOperations 和 MessageChannels 中应该保持一致")
    void taskReadyShouldBeConsistentAcrossClasses() {
      // Then
      assertThat(MessageOperations.TASK_READY).isEqualTo(MessageChannels.INGEST_TASK_READY);
    }

    @Test
    @DisplayName("LITERATURE_READY 在 MessageOperations 和 MessageChannels 中应该保持一致")
    void literatureReadyShouldBeConsistentAcrossClasses() {
      // Then
      assertThat(MessageOperations.LITERATURE_READY)
          .isEqualTo(MessageChannels.INGEST_LITERATURE_READY);
    }
  }
}
