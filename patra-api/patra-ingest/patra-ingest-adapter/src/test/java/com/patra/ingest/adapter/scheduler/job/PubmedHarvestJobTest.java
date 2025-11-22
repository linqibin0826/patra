package com.patra.ingest.adapter.scheduler.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/// PubmedHarvestJob 单元测试。
///
/// 测试策略: - Mock 基类的 executeScheduleJob 方法以隔离测试 - 验证 ProvenanceCode 和 OperationCode 返回正确值 - 测试
/// XXL-Job 入口方法正确调用基类模板方法
///
/// 不使用 @SpringBootTest - 纯单元测试,不依赖 Spring 容器
@ExtendWith(MockitoExtension.class)
@DisplayName("PubmedHarvestJob 单元测试")
class PubmedHarvestJobTest {

  @Spy @InjectMocks private PubmedHarvestJob pubmedHarvestJob;

  @Test
  @DisplayName("应该返回正确的 ProvenanceCode")
  void shouldReturnCorrectProvenanceCode() {
    // When
    ProvenanceCode result = pubmedHarvestJob.getProvenanceCode();

    // Then
    assertThat(result).isEqualTo(ProvenanceCode.PUBMED);
  }

  @Test
  @DisplayName("应该返回正确的 OperationCode")
  void shouldReturnCorrectOperationCode() {
    // When
    OperationCode result = pubmedHarvestJob.getOperationCode();

    // Then
    assertThat(result).isEqualTo(OperationCode.HARVEST);
  }

  @Test
  @DisplayName("run 方法应该成功调用基类的 executeScheduleJob")
  void run_shouldCallExecuteScheduleJobSuccessfully() {
    // Given
    doNothing().when(pubmedHarvestJob).executeScheduleJob(any());

    // When
    pubmedHarvestJob.run();

    // Then
    verify(pubmedHarvestJob).executeScheduleJob(any());
  }

  @Test
  @DisplayName("run 方法应该在基类抛出异常时传播异常")
  void run_shouldPropagateExceptionWhenExecuteScheduleJobFails() {
    // Given
    RuntimeException expectedException = new RuntimeException("执行失败");
    doThrow(expectedException).when(pubmedHarvestJob).executeScheduleJob(any());

    // When & Then
    try {
      pubmedHarvestJob.run();
    } catch (RuntimeException ex) {
      assertThat(ex).isEqualTo(expectedException);
      verify(pubmedHarvestJob).executeScheduleJob(any());
    }
  }
}
