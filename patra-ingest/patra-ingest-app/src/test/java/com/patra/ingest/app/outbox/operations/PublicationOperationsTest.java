package com.patra.ingest.app.outbox.operations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PublicationOperations 枚举测试")
class PublicationOperationsTest {

  @Test
  @DisplayName("DATA_READY 枚举值应该存在")
  void dataReadyShouldExist() {
    assertThat(PublicationOperations.DATA_READY).isNotNull();
  }

  @Test
  @DisplayName("应该包含 1 个枚举值")
  void shouldHaveOneValue() {
    assertThat(PublicationOperations.values()).hasSize(1);
  }

  @Test
  @DisplayName("DATA_READY.getCode() 应该返回 'DATA_READY'")
  void dataReadyCodeShouldBeDataReady() {
    assertThat(PublicationOperations.DATA_READY.getCode()).isEqualTo("DATA_READY");
  }

  @Test
  @DisplayName("所有枚举值都应该有非空描述")
  void allValuesShouldHaveDescription() {
    for (PublicationOperations op : PublicationOperations.values()) {
      assertThat(op.getDescription()).isNotBlank();
    }
  }

  @Test
  @DisplayName("所有枚举值都应该实现 OperationType 接口")
  void allValuesShouldImplementOperationType() {
    for (PublicationOperations op : PublicationOperations.values()) {
      assertThat(op).isInstanceOf(com.patra.ingest.domain.messaging.OperationType.class);
    }
  }
}
