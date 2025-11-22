package com.patra.catalog.domain.model.aggregate;

import static org.assertj.core.api.Assertions.*;

import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.model.valueobject.TableProgress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// MeshImportAggregate 聚合根单元测试。
/// 
/// 测试策略：
/// 
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 测试领域行为和状态转换
///   - 验证业务不变性约束
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
/// 
/// 覆盖范围：
/// 
/// - ✅ startImport() - 开始导入任务
///   - ✅ updateTableProgress() - 更新表进度
///   - ✅ markAsCompleted() - 标记任务完成
///   - ✅ markAsFailed() - 标记任务失败
///   - ✅ retry() - 重试失败任务
///   - ✅ 状态转换验证
///   - ✅ 不变性约束验证
///   - ✅ 领域事件验证
/// 
/// @author Patra Team
/// @since 2.0
@DisplayName("MeshImportAggregate 单元测试")
class MeshImportAggregateTest {

  // ========== 聚合根创建测试 ==========

  @Nested
  @DisplayName("聚合根创建")
  class AggregateCreationTests {

    @Test
    @DisplayName("应该成功创建 PENDING 状态的导入任务")
    void shouldCreatePendingImportTask() {
      // Given: 任务基本信息
      MeshImportId id = MeshImportId.of(1001L);
      String taskName = "MeSH 2025 首次导入";
      String sourceUrl = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml";
      List<TableProgress> tableProgressList = createInitialTableProgressList();

      // When: 创建聚合根
      MeshImportAggregate aggregate =
          new MeshImportAggregate(
              id,
              taskName,
              MeshImportTaskStatus.PENDING,
              null, // startTime
              null, // endTime
              sourceUrl,
              null, // xmlFileHash
              null, // xmlFileSize
              tableProgressList,
              350000, // totalRecords
              0, // processedRecords
              0, // failedBatchCount
              null // lastErrorMessage
              );

      // Then: 验证初始状态
      assertThat(aggregate).isNotNull();
      assertThat(aggregate.getId()).isEqualTo(id);
      assertThat(aggregate.getTaskName()).isEqualTo(taskName);
      assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.PENDING);
      assertThat(aggregate.getSourceUrl()).isEqualTo(sourceUrl);
      assertThat(aggregate.getTotalRecords()).isEqualTo(350000);
      assertThat(aggregate.getProcessedRecords()).isEqualTo(0);
      assertThat(aggregate.getFailedBatchCount()).isEqualTo(0);
      assertThat(aggregate.getTableProgressList()).hasSize(5);
    }
  }

  // ========== startImport() 行为测试 ==========

  @Nested
  @DisplayName("开始导入任务")
  class StartImportTests {

    @Test
    @DisplayName("应该成功开始 PENDING 状态的任务")
    void shouldStartPendingTask() {
      // Given: PENDING 状态的任务
      MeshImportAggregate aggregate = createPendingAggregate();

      // When: 开始导入
      aggregate.startImport();

      // Then: 状态变为 PROCESSING，记录开始时间
      assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.PROCESSING);
      assertThat(aggregate.getStartTime()).isNotNull();
      assertThat(aggregate.getStartTime()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("应该发布 MeshImportStarted 领域事件")
    void shouldPublishMeshImportStartedEvent() {
      // Given: PENDING 状态的任务
      MeshImportAggregate aggregate = createPendingAggregate();

      // When: 开始导入
      aggregate.startImport();

      // Then: 验证领域事件
      assertThat(aggregate.getDomainEvents()).hasSize(1);
      assertThat(aggregate.getDomainEvents().get(0))
          .isInstanceOf(com.patra.catalog.domain.event.MeshImportStarted.class);
    }

    @Test
    @DisplayName("应该抛出异常当任务不是 PENDING 状态")
    void shouldThrowExceptionWhenNotPending() {
      // Given: PROCESSING 状态的任务
      MeshImportAggregate aggregate = createProcessingAggregate();

      // When & Then: 尝试开始任务应该失败
      assertThatThrownBy(() -> aggregate.startImport())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("只有 PENDING 状态的任务可以开始导入");
    }

    @Test
    @DisplayName("应该抛出异常当任务已完成")
    void shouldThrowExceptionWhenAlreadyCompleted() {
      // Given: SUCCESS 状态的任务
      MeshImportAggregate aggregate = createCompletedAggregate();

      // When & Then: 尝试开始任务应该失败
      assertThatThrownBy(() -> aggregate.startImport())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("只有 PENDING 状态的任务可以开始导入");
    }
  }

  // ========== updateTableProgress() 行为测试 ==========

  @Nested
  @DisplayName("更新表进度")
  class UpdateTableProgressTests {

    @Test
    @DisplayName("应该成功更新指定表的进度")
    void shouldUpdateTableProgress() {
      // Given: PROCESSING 状态的任务
      MeshImportAggregate aggregate = createProcessingAggregate();
      String tableName = "descriptor";
      Integer processedCount = 5000;
      Integer lastBatchNum = 5;

      // When: 更新表进度
      aggregate.updateTableProgress(tableName, processedCount, lastBatchNum);

      // Then: 验证表进度已更新
      TableProgress progress =
          aggregate.getTableProgressList().stream()
              .filter(p -> p.getTableName().equals(tableName))
              .findFirst()
              .orElseThrow();

      assertThat(progress.getProcessedCount()).isEqualTo(processedCount);
      assertThat(progress.getLastBatchNum()).isEqualTo(lastBatchNum);
    }

    @Test
    @DisplayName("应该重新计算整体进度")
    void shouldRecalculateOverallProgress() {
      // Given: PROCESSING 状态的任务
      MeshImportAggregate aggregate = createProcessingAggregate();

      // When: 更新多个表的进度
      aggregate.updateTableProgress("descriptor", 5000, 5);
      aggregate.updateTableProgress("qualifier", 100, 1);

      // Then: 整体进度应该是两表之和
      assertThat(aggregate.getProcessedRecords()).isEqualTo(5100);
    }

    @Test
    @DisplayName("应该抛出异常当表不存在")
    void shouldThrowExceptionWhenTableNotExists() {
      // Given: PROCESSING 状态的任务
      MeshImportAggregate aggregate = createProcessingAggregate();

      // When & Then: 更新不存在的表应该失败
      assertThatThrownBy(() -> aggregate.updateTableProgress("invalid_table", 100, 1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("表不存在");
    }
  }

  // ========== markAsCompleted() 行为测试 ==========

  @Nested
  @DisplayName("标记任务完成")
  class MarkAsCompletedTests {

    @Test
    @DisplayName("应该成功标记所有表完成的任务")
    void shouldMarkAsCompletedWhenAllTablesCompleted() {
      // Given: 所有表都完成的任务
      MeshImportAggregate aggregate = createAllTablesCompletedAggregate();

      // When: 标记任务完成
      aggregate.markAsCompleted();

      // Then: 状态变为 SUCCESS，记录结束时间
      assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.SUCCESS);
      assertThat(aggregate.getEndTime()).isNotNull();
      assertThat(aggregate.getEndTime()).isAfterOrEqualTo(aggregate.getStartTime());
    }

    @Test
    @DisplayName("应该发布 MeshImportCompleted 领域事件")
    void shouldPublishMeshImportCompletedEvent() {
      // Given: 所有表都完成的任务
      MeshImportAggregate aggregate = createAllTablesCompletedAggregate();

      // When: 标记任务完成
      aggregate.markAsCompleted();

      // Then: 验证领域事件
      assertThat(aggregate.getDomainEvents())
          .hasSize(1)
          .first()
          .isInstanceOf(com.patra.catalog.domain.event.MeshImportCompleted.class);
    }

    @Test
    @DisplayName("应该抛出异常当有表未完成")
    void shouldThrowExceptionWhenTablesNotCompleted() {
      // Given: 有表未完成的任务
      MeshImportAggregate aggregate = createProcessingAggregate();

      // When & Then: 标记完成应该失败
      assertThatThrownBy(() -> aggregate.markAsCompleted())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("所有表必须完成才能标记任务完成");
    }
  }

  // ========== markAsFailed() 行为测试 ==========

  @Nested
  @DisplayName("标记任务失败")
  class MarkAsFailedTests {

    @Test
    @DisplayName("应该成功标记任务失败")
    void shouldMarkAsFailed() {
      // Given: PROCESSING 状态的任务
      MeshImportAggregate aggregate = createProcessingAggregate();
      String errorMessage = "XML 文件解析失败：格式错误";

      // When: 标记任务失败
      aggregate.markAsFailed(errorMessage);

      // Then: 状态变为 FAILED，记录错误信息和结束时间
      assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.FAILED);
      assertThat(aggregate.getLastErrorMessage()).isEqualTo(errorMessage);
      assertThat(aggregate.getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("应该发布 MeshImportFailed 领域事件")
    void shouldPublishMeshImportFailedEvent() {
      // Given: PROCESSING 状态的任务
      MeshImportAggregate aggregate = createProcessingAggregate();

      // When: 标记任务失败
      aggregate.markAsFailed("测试错误");

      // Then: 验证领域事件
      assertThat(aggregate.getDomainEvents())
          .hasSize(1)
          .first()
          .isInstanceOf(com.patra.catalog.domain.event.MeshImportFailed.class);
    }
  }

  // ========== retry() 行为测试 ==========

  @Nested
  @DisplayName("重试失败任务")
  class RetryTests {

    @Test
    @DisplayName("应该成功重试 FAILED 状态的任务")
    void shouldRetryFailedTask() {
      // Given: FAILED 状态的任务
      MeshImportAggregate aggregate = createFailedAggregate();

      // When: 重试任务
      aggregate.retry();

      // Then: 状态变为 PROCESSING，重置失败批次和错误信息
      assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.PROCESSING);
      assertThat(aggregate.getFailedBatchCount()).isEqualTo(0);
      assertThat(aggregate.getLastErrorMessage()).isNull();
    }

    @Test
    @DisplayName("应该抛出异常当任务不是 FAILED 状态")
    void shouldThrowExceptionWhenNotFailed() {
      // Given: PROCESSING 状态的任务
      MeshImportAggregate aggregate = createProcessingAggregate();

      // When & Then: 重试非失败任务应该失败
      assertThatThrownBy(() -> aggregate.retry())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("只有 FAILED 状态的任务可以重试");
    }

    @Test
    @DisplayName("应该抛出异常当任务已完成")
    void shouldThrowExceptionWhenAlreadyCompletedForRetry() {
      // Given: SUCCESS 状态的任务
      MeshImportAggregate aggregate = createCompletedAggregate();

      // When & Then: 重试已完成任务应该失败
      assertThatThrownBy(() -> aggregate.retry())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("只有 FAILED 状态的任务可以重试");
    }
  }

  // ========== 进度计算测试 (T042 - 用户故事 2) ==========

  @Nested
  @DisplayName("进度计算")
  class ProgressCalculationTests {

    @Test
    @DisplayName("应该正确计算处理速度（记录数/秒）")
    void shouldCalculateProcessSpeed() {
      // Given: PROCESSING 状态的任务，已运行一段时间
      MeshImportAggregate aggregate = createProcessingAggregate();
      // 模拟开始时间为 1 分钟前
      Instant startTime = Instant.now().minusSeconds(60);
      aggregate =
          new MeshImportAggregate(
              aggregate.getId(),
              aggregate.getTaskName(),
              MeshImportTaskStatus.PROCESSING,
              startTime,
              null,
              aggregate.getSourceUrl(),
              aggregate.getXmlFileHash(),
              aggregate.getXmlFileSize(),
              aggregate.getTableProgressList(),
              aggregate.getTotalRecords(),
              6000, // 已处理 6000 条
              0,
              null);

      // When: 计算处理速度
      Double speed = aggregate.calculateProcessSpeed();

      // Then: 速度应该约为 100 记录/秒（6000/60）
      assertThat(speed).isNotNull().isGreaterThan(99.0).isLessThan(101.0);
    }

    @Test
    @DisplayName("当任务未开始时应该返回 null")
    void shouldReturnNullSpeedWhenNotStarted() {
      // Given: PENDING 状态的任务（未开始）
      MeshImportAggregate aggregate = createPendingAggregate();

      // When: 计算处理速度
      Double speed = aggregate.calculateProcessSpeed();

      // Then: 速度应该为 null
      assertThat(speed).isNull();
    }

    @Test
    @DisplayName("当处理记录数为 0 时应该返回 0.0")
    void shouldReturnZeroSpeedWhenNoRecordsProcessed() {
      // Given: PROCESSING 状态但未处理任何记录的任务
      MeshImportAggregate aggregate = createProcessingAggregate();

      // When: 计算处理速度
      Double speed = aggregate.calculateProcessSpeed();

      // Then: 速度应该为 0.0
      assertThat(speed).isNotNull().isEqualTo(0.0);
    }

    @Test
    @DisplayName("应该正确估算剩余时间（秒）")
    void shouldEstimateRemainingTime() {
      // Given: PROCESSING 状态的任务，已处理一部分数据
      Instant startTime = Instant.now().minusSeconds(100);
      MeshImportAggregate aggregate =
          new MeshImportAggregate(
              MeshImportId.of(1001L),
              "MeSH 2025 首次导入",
              MeshImportTaskStatus.PROCESSING,
              startTime,
              null,
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
              null,
              null,
              createInitialTableProgressList(),
              10000, // 总共 10000 条
              5000, // 已处理 5000 条（50%）
              0,
              null);

      // When: 估算剩余时间
      Long remainingSeconds = aggregate.estimateRemainingTime();

      // Then: 剩余时间应该约为 100 秒（已用时 100s 完成 50%，剩余 50% 预计 100s）
      assertThat(remainingSeconds).isNotNull().isGreaterThan(90L).isLessThan(110L);
    }

    @Test
    @DisplayName("当任务未开始时剩余时间应该返回 null")
    void shouldReturnNullRemainingTimeWhenNotStarted() {
      // Given: PENDING 状态的任务
      MeshImportAggregate aggregate = createPendingAggregate();

      // When: 估算剩余时间
      Long remainingSeconds = aggregate.estimateRemainingTime();

      // Then: 剩余时间应该为 null
      assertThat(remainingSeconds).isNull();
    }

    @Test
    @DisplayName("当处理记录数为 0 时剩余时间应该返回 null")
    void shouldReturnNullRemainingTimeWhenNoProgress() {
      // Given: PROCESSING 状态但未处理任何记录的任务
      MeshImportAggregate aggregate = createProcessingAggregate();

      // When: 估算剩余时间
      Long remainingSeconds = aggregate.estimateRemainingTime();

      // Then: 剩余时间应该为 null（无法估算）
      assertThat(remainingSeconds).isNull();
    }

    @Test
    @DisplayName("当所有记录已处理完时剩余时间应该返回 0")
    void shouldReturnZeroRemainingTimeWhenCompleted() {
      // Given: 所有记录已处理的任务
      MeshImportAggregate aggregate =
          new MeshImportAggregate(
              MeshImportId.of(1001L),
              "MeSH 2025 首次导入",
              MeshImportTaskStatus.PROCESSING,
              Instant.now().minusSeconds(100),
              null,
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
              null,
              null,
              createInitialTableProgressList(),
              10000, // 总共 10000 条
              10000, // 已处理 10000 条（100%）
              0,
              null);

      // When: 估算剩余时间
      Long remainingSeconds = aggregate.estimateRemainingTime();

      // Then: 剩余时间应该为 0
      assertThat(remainingSeconds).isNotNull().isEqualTo(0L);
    }

    @Test
    @DisplayName("应该正确计算整体进度百分比")
    void shouldCalculateOverallProgress() {
      // Given: 部分完成的任务
      MeshImportAggregate aggregate =
          new MeshImportAggregate(
              MeshImportId.of(1001L),
              "MeSH 2025 首次导入",
              MeshImportTaskStatus.PROCESSING,
              Instant.now().minusSeconds(100),
              null,
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
              null,
              null,
              createInitialTableProgressList(),
              10000, // 总共 10000 条
              7500, // 已处理 7500 条（75%）
              0,
              null);

      // When: 计算整体进度
      Double overallProgress = aggregate.getOverallProgress();

      // Then: 进度应该为 75%
      assertThat(overallProgress).isNotNull().isEqualTo(75.0);
    }

    @Test
    @DisplayName("当总记录数为 0 时进度应该返回 0.0")
    void shouldReturnZeroProgressWhenNoTotalRecords() {
      // Given: 总记录数为 0 的任务
      MeshImportAggregate aggregate =
          new MeshImportAggregate(
              MeshImportId.of(1001L),
              "MeSH 2025 首次导入",
              MeshImportTaskStatus.PROCESSING,
              Instant.now(),
              null,
              "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
              null,
              null,
              createInitialTableProgressList(),
              0, // 总共 0 条
              0, // 已处理 0 条
              0,
              null);

      // When: 计算整体进度
      Double overallProgress = aggregate.getOverallProgress();

      // Then: 进度应该为 0.0
      assertThat(overallProgress).isNotNull().isEqualTo(0.0);
    }
  }

  // ========== 状态转换测试 ==========

  @Nested
  @DisplayName("状态转换验证")
  class StateTransitionTests {

    @Test
    @DisplayName("应该正确执行完整生命周期：PENDING → PROCESSING → SUCCESS")
    void shouldFollowSuccessLifecycle() {
      // Given: 新创建的任务
      MeshImportAggregate aggregate = createPendingAggregate();

      // When & Then: 执行完整生命周期
      // PENDING → PROCESSING
      aggregate.startImport();
      assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.PROCESSING);

      // 完成所有表
      markAllTablesCompleted(aggregate);

      // PROCESSING → SUCCESS
      aggregate.markAsCompleted();
      assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.SUCCESS);
    }

    @Test
    @DisplayName("应该正确执行失败重试流程：PENDING → PROCESSING → FAILED → PROCESSING")
    void shouldFollowRetryLifecycle() {
      // Given: 新创建的任务
      MeshImportAggregate aggregate = createPendingAggregate();

      // When & Then: 执行失败重试流程
      // PENDING → PROCESSING
      aggregate.startImport();
      assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.PROCESSING);

      // PROCESSING → FAILED
      aggregate.markAsFailed("测试失败");
      assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.FAILED);

      // FAILED → PROCESSING (重试)
      aggregate.retry();
      assertThat(aggregate.getStatus()).isEqualTo(MeshImportTaskStatus.PROCESSING);
    }

    @Test
    @DisplayName("不应该允许从 SUCCESS 转换到其他状态")
    void shouldNotAllowTransitionFromSuccess() {
      // Given: SUCCESS 状态的任务
      MeshImportAggregate aggregate = createCompletedAggregate();

      // When & Then: 所有状态转换都应该失败
      assertThatThrownBy(() -> aggregate.startImport())
          .isInstanceOf(IllegalStateException.class);

      assertThatThrownBy(() -> aggregate.retry()).isInstanceOf(IllegalStateException.class);
    }
  }

  // ========== 辅助方法 ==========

  /// 创建初始表进度列表（5张表：descriptor, qualifier, treeNumber, entryTerm, concept）
  private static List<TableProgress> createInitialTableProgressList() {
    List<TableProgress> progressList = new ArrayList<>();
    progressList.add(
        TableProgress.builder()
            .tableName("descriptor")
            .totalCount(35000)
            .processedCount(0)
            .failedCount(0)
            .status(MeshTableImportStatus.NOT_STARTED)
            .lastBatchNum(0)
            .build());
    progressList.add(
        TableProgress.builder()
            .tableName("qualifier")
            .totalCount(100)
            .processedCount(0)
            .failedCount(0)
            .status(MeshTableImportStatus.NOT_STARTED)
            .lastBatchNum(0)
            .build());
    progressList.add(
        TableProgress.builder()
            .tableName("treeNumber")
            .totalCount(80000)
            .processedCount(0)
            .failedCount(0)
            .status(MeshTableImportStatus.NOT_STARTED)
            .lastBatchNum(0)
            .build());
    progressList.add(
        TableProgress.builder()
            .tableName("entryTerm")
            .totalCount(250000)
            .processedCount(0)
            .failedCount(0)
            .status(MeshTableImportStatus.NOT_STARTED)
            .lastBatchNum(0)
            .build());
    progressList.add(
        TableProgress.builder()
            .tableName("concept")
            .totalCount(180000)
            .processedCount(0)
            .failedCount(0)
            .status(MeshTableImportStatus.NOT_STARTED)
            .lastBatchNum(0)
            .build());
    return progressList;
  }

  /// 创建 PENDING 状态的聚合根
  private static MeshImportAggregate createPendingAggregate() {
    return new MeshImportAggregate(
        MeshImportId.of(1001L),
        "MeSH 2025 首次导入",
        MeshImportTaskStatus.PENDING,
        null,
        null,
        "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
        null,
        null,
        createInitialTableProgressList(),
        350000,
        0,
        0,
        null);
  }

  /// 创建 PROCESSING 状态的聚合根
  private static MeshImportAggregate createProcessingAggregate() {
    MeshImportAggregate aggregate = createPendingAggregate();
    aggregate.startImport();
    aggregate.clearDomainEvents(); // 清除事件，避免干扰测试
    return aggregate;
  }

  /// 创建所有表都完成的聚合根
  private static MeshImportAggregate createAllTablesCompletedAggregate() {
    MeshImportAggregate aggregate = createProcessingAggregate();
    markAllTablesCompleted(aggregate);
    return aggregate;
  }

  /// 创建 SUCCESS 状态的聚合根
  private static MeshImportAggregate createCompletedAggregate() {
    MeshImportAggregate aggregate = createAllTablesCompletedAggregate();
    aggregate.markAsCompleted();
    aggregate.clearDomainEvents(); // 清除事件，避免干扰测试
    return aggregate;
  }

  /// 创建 FAILED 状态的聚合根
  private static MeshImportAggregate createFailedAggregate() {
    MeshImportAggregate aggregate = createProcessingAggregate();
    aggregate.markAsFailed("测试失败原因");
    aggregate.clearDomainEvents(); // 清除事件，避免干扰测试
    return aggregate;
  }

  /// 标记所有表为 COMPLETED
  private static void markAllTablesCompleted(MeshImportAggregate aggregate) {
    // 创建一个副本以避免在迭代时修改列表
    List<TableProgress> progressListCopy = new ArrayList<>(aggregate.getTableProgressList());

    for (TableProgress progress : progressListCopy) {
      // 使用 updateTableProgress 方法更新进度，它会自动计算状态为 COMPLETED
      aggregate.updateTableProgress(
          progress.getTableName(),
          progress.getTotalCount(),  // processedCount = totalCount（完成）
          progress.getTotalCount() / 1000);  // lastBatchNum
    }
  }
}
