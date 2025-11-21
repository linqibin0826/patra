package com.patra.catalog.app.usecase.meshimport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.patra.catalog.api.dto.MeshProgressDTO;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import com.patra.catalog.domain.model.valueobject.FailedBatch;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.model.valueobject.TableProgress;
import com.patra.catalog.domain.port.MeshBatchDetailPort;
import com.patra.catalog.domain.port.MeshImportPort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MeshProgressQueryOrchestrator 单元测试。
 *
 * <p><b>测试策略</b>：
 *
 * <ul>
 *   <li>Mock 所有 Ports（MeshImportPort、MeshBatchDetailPort）
 *   <li>验证调用顺序（InOrder）
 *   <li>验证 DTO 组装正确性
 *   <li>覆盖正常场景和异常场景
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0 (User Story 2 - 实时监控导入进度)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MeshProgressQueryOrchestrator 单元测试")
class MeshProgressQueryOrchestratorTest {

  @Mock private MeshImportPort meshImportPort;

  @Mock private MeshBatchDetailPort meshBatchDetailPort;

  @InjectMocks private MeshProgressQueryOrchestrator orchestrator;

  @Test
  @DisplayName("应该能够查询正在进行的任务进度")
  void shouldQueryProgressForRunningTask() {
    // Given: 创建一个正在运行的任务
    MeshImportId importId = new MeshImportId(1001L);
    Instant startTime = Instant.now().minus(Duration.ofMinutes(5));

    List<TableProgress> tableProgressList = new ArrayList<>();
    tableProgressList.add(
        TableProgress.builder()
            .tableName("descriptor")
            .totalCount(30000)
            .processedCount(15000)
            .failedCount(0)
            .status(MeshTableImportStatus.IN_PROGRESS)
            .lastBatchNum(15)
            .lastUpdateTime(Instant.now())
            .build());
    tableProgressList.add(
        TableProgress.builder()
            .tableName("qualifier")
            .totalCount(100)
            .processedCount(0)
            .failedCount(0)
            .status(MeshTableImportStatus.NOT_STARTED)
            .lastBatchNum(0)
            .lastUpdateTime(Instant.now())
            .build());

    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            importId,
            "2025年MeSH数据导入",
            MeshImportTaskStatus.PROCESSING,
            startTime,
            null, // endTime
            "https://nlm.nih.gov/mesh/desc2025.xml",
            "abc123hash",
            1024000L,
            tableProgressList,
            30100, // totalRecords
            15000, // processedRecords
            0, // failedBatchCount
            null // lastErrorMessage
            );

    List<FailedBatch> failedBatches = new ArrayList<>();

    // When: 调用查询进度方法
    given(meshImportPort.findById(importId)).willReturn(Optional.of(aggregate));
    given(meshBatchDetailPort.findFailedBatches(importId)).willReturn(failedBatches);

    MeshProgressDTO result = orchestrator.queryProgress(importId);

    // Then: 验证调用顺序
    InOrder inOrder = inOrder(meshImportPort, meshBatchDetailPort);
    inOrder.verify(meshImportPort).findById(importId);
    inOrder.verify(meshBatchDetailPort).findFailedBatches(importId);

    // Then: 验证 DTO 内容
    assertThat(result).isNotNull();
    assertThat(result.taskId()).isEqualTo("1001");
    assertThat(result.taskName()).isEqualTo("2025年MeSH数据导入");
    assertThat(result.status()).isEqualTo("processing"); // 枚举的code是小写
    assertThat(result.totalRecords()).isEqualTo(30100);
    assertThat(result.processedRecords()).isEqualTo(15000);

    // Then: 验证进度计算
    assertThat(result.overallProgress()).isCloseTo(49.83, within(0.01)); // 15000/30100 * 100
    assertThat(result.processSpeed()).isNotNull(); // 应该有处理速度
    assertThat(result.processSpeed()).isGreaterThan(0.0);
    assertThat(result.estimatedRemainingSeconds()).isNotNull(); // 应该有剩余时间

    // Then: 验证时间字段
    assertThat(result.startTime()).isEqualTo(startTime);
    assertThat(result.endTime()).isNull();
    assertThat(result.elapsedSeconds()).isGreaterThan(0L);

    // Then: 验证表进度列表
    assertThat(result.tableProgress()).hasSize(2);
    MeshProgressDTO.TableProgressDTO descriptorProgress = result.tableProgress().get(0);
    assertThat(descriptorProgress.tableName()).isEqualTo("descriptor");
    assertThat(descriptorProgress.totalCount()).isEqualTo(30000);
    assertThat(descriptorProgress.processedCount()).isEqualTo(15000);
    assertThat(descriptorProgress.progressPercentage()).isCloseTo(50.0, within(0.01));
    assertThat(descriptorProgress.status()).isEqualTo("in_progress"); // 枚举的code是小写

    // Then: 验证失败批次列表为空
    assertThat(result.failedBatches()).isEmpty();
  }

  @Test
  @DisplayName("应该能够查询带失败批次的任务进度")
  void shouldQueryProgressWithFailedBatches() {
    // Given: 创建一个有失败批次的任务
    MeshImportId importId = new MeshImportId(1002L);
    Instant startTime = Instant.now().minus(Duration.ofMinutes(10));
    Instant failureTime = Instant.now().minus(Duration.ofMinutes(2));

    List<TableProgress> tableProgressList = new ArrayList<>();
    tableProgressList.add(
        TableProgress.builder()
            .tableName("descriptor")
            .totalCount(30000)
            .processedCount(20000)
            .failedCount(100)
            .status(MeshTableImportStatus.IN_PROGRESS)
            .lastBatchNum(20)
            .lastUpdateTime(Instant.now())
            .build());

    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            importId,
            "2025年MeSH数据导入（有失败）",
            MeshImportTaskStatus.PROCESSING,
            startTime,
            null,
            "https://nlm.nih.gov/mesh/desc2025.xml",
            "abc123hash",
            1024000L,
            tableProgressList,
            30000,
            20000,
            2, // failedBatchCount
            "部分批次导入失败");

    List<FailedBatch> failedBatches = new ArrayList<>();
    failedBatches.add(
        FailedBatch.builder()
            .batchId(1001L)
            .tableName("descriptor")
            .batchNum(5)
            .failureReason("数据库连接超时")
            .failureTime(failureTime)
            .retryCount(1)
            .build());
    failedBatches.add(
        FailedBatch.builder()
            .batchId(1002L)
            .tableName("descriptor")
            .batchNum(12)
            .failureReason("数据解析错误")
            .failureTime(failureTime)
            .retryCount(0)
            .build());

    // When: 调用查询进度方法
    given(meshImportPort.findById(importId)).willReturn(Optional.of(aggregate));
    given(meshBatchDetailPort.findFailedBatches(importId)).willReturn(failedBatches);

    MeshProgressDTO result = orchestrator.queryProgress(importId);

    // Then: 验证失败批次
    assertThat(result.failedBatches()).hasSize(2);

    MeshProgressDTO.FailedBatchDTO firstFailure = result.failedBatches().get(0);
    assertThat(firstFailure.batchId()).isEqualTo(1001L);
    assertThat(firstFailure.tableName()).isEqualTo("descriptor");
    assertThat(firstFailure.batchNum()).isEqualTo(5);
    assertThat(firstFailure.failureReason()).isEqualTo("数据库连接超时");
    assertThat(firstFailure.retryCount()).isEqualTo(1);

    MeshProgressDTO.FailedBatchDTO secondFailure = result.failedBatches().get(1);
    assertThat(secondFailure.batchId()).isEqualTo(1002L);
    assertThat(secondFailure.batchNum()).isEqualTo(12);
  }

  @Test
  @DisplayName("应该能够查询已完成任务的进度")
  void shouldQueryProgressForCompletedTask() {
    // Given: 创建一个已完成的任务
    MeshImportId importId = new MeshImportId(1003L);
    Instant startTime = Instant.now().minus(Duration.ofMinutes(30));
    Instant endTime = Instant.now();

    List<TableProgress> tableProgressList = new ArrayList<>();
    tableProgressList.add(
        TableProgress.builder()
            .tableName("descriptor")
            .totalCount(30000)
            .processedCount(30000)
            .failedCount(0)
            .status(MeshTableImportStatus.COMPLETED)
            .lastBatchNum(30)
            .lastUpdateTime(endTime)
            .build());

    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            importId,
            "2025年MeSH数据导入（已完成）",
            MeshImportTaskStatus.SUCCESS,
            startTime,
            endTime,
            "https://nlm.nih.gov/mesh/desc2025.xml",
            "abc123hash",
            1024000L,
            tableProgressList,
            30000,
            30000,
            0,
            null);

    // When: 调用查询进度方法
    given(meshImportPort.findById(importId)).willReturn(Optional.of(aggregate));
    given(meshBatchDetailPort.findFailedBatches(importId)).willReturn(List.of());

    MeshProgressDTO result = orchestrator.queryProgress(importId);

    // Then: 验证已完成状态
    assertThat(result.status()).isEqualTo("success"); // 枚举的code是小写
    assertThat(result.overallProgress()).isEqualTo(100.0);
    assertThat(result.endTime()).isEqualTo(endTime);
    assertThat(result.estimatedRemainingSeconds()).isEqualTo(0L); // 已完成，剩余时间为0
  }

  @Test
  @DisplayName("当任务不存在时应该抛出异常")
  void shouldThrowExceptionWhenTaskNotFound() {
    // Given: 任务不存在
    MeshImportId importId = new MeshImportId(9999L);
    given(meshImportPort.findById(importId)).willReturn(Optional.empty());

    // When & Then: 抛出异常
    assertThatThrownBy(() -> orchestrator.queryProgress(importId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("任务不存在");
  }

  @Test
  @DisplayName("应该能够查询未开始任务的进度（所有字段为初始值）")
  void shouldQueryProgressForPendingTask() {
    // Given: 创建一个 PENDING 状态的任务
    MeshImportId importId = new MeshImportId(1004L);

    List<TableProgress> tableProgressList = new ArrayList<>();
    tableProgressList.add(
        TableProgress.builder()
            .tableName("descriptor")
            .totalCount(30000)
            .processedCount(0)
            .failedCount(0)
            .status(MeshTableImportStatus.NOT_STARTED)
            .lastBatchNum(0)
            .lastUpdateTime(Instant.now())
            .build());

    MeshImportAggregate aggregate =
        new MeshImportAggregate(
            importId,
            "2025年MeSH数据导入（未开始）",
            MeshImportTaskStatus.PENDING,
            null, // startTime 为 null
            null,
            "https://nlm.nih.gov/mesh/desc2025.xml",
            null,
            null,
            tableProgressList,
            30000,
            0,
            0,
            null);

    // When: 调用查询进度方法
    given(meshImportPort.findById(importId)).willReturn(Optional.of(aggregate));
    given(meshBatchDetailPort.findFailedBatches(importId)).willReturn(List.of());

    MeshProgressDTO result = orchestrator.queryProgress(importId);

    // Then: 验证未开始状态
    assertThat(result.status()).isEqualTo("pending"); // 枚举的code是小写
    assertThat(result.overallProgress()).isEqualTo(0.0);
    assertThat(result.processSpeed()).isNull(); // 未开始，速度为 null
    assertThat(result.estimatedRemainingSeconds()).isNull(); // 未开始，无法估算
    assertThat(result.startTime()).isNull();
    assertThat(result.elapsedSeconds()).isEqualTo(0L);
  }
}
