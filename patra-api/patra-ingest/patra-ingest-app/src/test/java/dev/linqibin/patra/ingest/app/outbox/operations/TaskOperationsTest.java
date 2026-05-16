package dev.linqibin.patra.ingest.app.outbox.operations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TaskOperations 枚举测试")
class TaskOperationsTest {

  @Test
  @DisplayName("READY 枚举值应该存在")
  void readyShouldExist() {
    assertThat(TaskOperations.READY).isNotNull();
  }

  @Test
  @DisplayName("应该包含 1 个枚举值")
  void shouldHaveOneValue() {
    assertThat(TaskOperations.values()).hasSize(1);
  }

  @Test
  @DisplayName("READY.getCode() 应该返回 'READY'")
  void readyCodeShouldBeReady() {
    assertThat(TaskOperations.READY.getCode()).isEqualTo("READY");
  }

  @Test
  @DisplayName("READY 应该有非空描述")
  void readyShouldHaveDescription() {
    assertThat(TaskOperations.READY.getDescription()).isNotBlank();
  }

  @Test
  @DisplayName("所有枚举值都应该有非空描述")
  void allValuesShouldHaveDescription() {
    for (TaskOperations op : TaskOperations.values()) {
      assertThat(op.getDescription()).isNotBlank();
    }
  }

  @Test
  @DisplayName("所有枚举值都应该实现 OperationType 接口")
  void allValuesShouldImplementOperationType() {
    for (TaskOperations op : TaskOperations.values()) {
      assertThat(op).isInstanceOf(dev.linqibin.patra.ingest.domain.messaging.OperationType.class);
    }
  }
}
