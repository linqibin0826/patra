package com.patra.catalog.domain.model.valueobject;

import static org.assertj.core.api.Assertions.*;

import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// TableProgress 值对象单元测试。
/// 
/// 测试策略：
/// 
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 验证值对象不可变性
///   - 测试进度计算逻辑
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
/// 
/// 覆盖范围：
/// 
/// - ✅ getProgressPercentage() - 进度百分比计算
///   - ✅ updateProgress() - 更新进度（返回新实例）
///   - ✅ incrementFailedCount() - 增加失败数（返回新实例）
///   - ✅ 不可变性验证
///   - ✅ 状态自动计算
/// 
/// @author Patra Team
/// @since 2.0
@DisplayName("TableProgress 单元测试")
class TableProgressTest {

  // ========== 进度百分比计算测试 ==========

  @Nested
  @DisplayName("进度百分比计算")
  class ProgressPercentageTests {

    @Test
    @DisplayName("应该返回 0% 当未开始处理")
    void shouldReturnZeroPercentageWhenNotStarted() {
      // Given: 未开始的表进度
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(0)
              .failedCount(0)
              .status(MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .build();

      // When: 计算进度百分比
      Double percentage = progress.getProgressPercentage();

      // Then: 应该为 0%
      assertThat(percentage).isEqualTo(0.0);
    }

    @Test
    @DisplayName("应该返回正确百分比当部分完成")
    void shouldReturnCorrectPercentageWhenPartiallyCompleted() {
      // Given: 部分完成的表进度
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(5000)
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      // When: 计算进度百分比
      Double percentage = progress.getProgressPercentage();

      // Then: 应该为 14.29%
      assertThat(percentage).isCloseTo(14.29, within(0.01));
    }

    @Test
    @DisplayName("应该返回 100% 当全部完成")
    void shouldReturnHundredPercentageWhenCompleted() {
      // Given: 全部完成的表进度
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(35000)
              .failedCount(0)
              .status(MeshTableImportStatus.COMPLETED)
              .lastBatchNum(35)
              .build();

      // When: 计算进度百分比
      Double percentage = progress.getProgressPercentage();

      // Then: 应该为 100%
      assertThat(percentage).isEqualTo(100.0);
    }

    @Test
    @DisplayName("应该返回 0% 当总数为 0")
    void shouldReturnZeroPercentageWhenTotalCountIsZero() {
      // Given: 总数为 0 的表进度
      TableProgress progress =
          TableProgress.builder()
              .tableName("empty_table")
              .totalCount(0)
              .processedCount(0)
              .failedCount(0)
              .status(MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .build();

      // When: 计算进度百分比
      Double percentage = progress.getProgressPercentage();

      // Then: 应该为 0%
      assertThat(percentage).isEqualTo(0.0);
    }

    @Test
    @DisplayName("应该返回 0% 当总数为 null")
    void shouldReturnZeroPercentageWhenTotalCountIsNull() {
      // Given: 总数为 null 的表进度
      TableProgress progress =
          TableProgress.builder()
              .tableName("unknown_table")
              .totalCount(null)
              .processedCount(0)
              .failedCount(0)
              .status(MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .build();

      // When: 计算进度百分比
      Double percentage = progress.getProgressPercentage();

      // Then: 应该为 0%
      assertThat(percentage).isEqualTo(0.0);
    }
  }

  // ========== 更新进度测试 ==========

  @Nested
  @DisplayName("更新进度")
  class UpdateProgressTests {

    @Test
    @DisplayName("应该返回新实例当更新进度")
    void shouldReturnNewInstanceWhenUpdateProgress() {
      // Given: 初始表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(0)
              .failedCount(0)
              .status(MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .build();

      // When: 更新进度
      TableProgress updated = original.updateProgress(5000, 5);

      // Then: 应该返回新实例，原实例不变
      assertThat(updated).isNotSameAs(original);
      assertThat(original.getProcessedCount()).isEqualTo(0);
      assertThat(original.getLastBatchNum()).isEqualTo(0);
      assertThat(updated.getProcessedCount()).isEqualTo(5000);
      assertThat(updated.getLastBatchNum()).isEqualTo(5);
    }

    @Test
    @DisplayName("应该自动计算状态为 IN_PROGRESS 当部分完成")
    void shouldCalculateStatusAsInProgressWhenPartiallyCompleted() {
      // Given: 初始表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(0)
              .failedCount(0)
              .status(MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .build();

      // When: 更新进度为部分完成
      TableProgress updated = original.updateProgress(5000, 5);

      // Then: 状态应该变为 IN_PROGRESS
      assertThat(updated.getStatus()).isEqualTo(MeshTableImportStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("应该自动计算状态为 COMPLETED 当全部完成")
    void shouldCalculateStatusAsCompletedWhenFullyCompleted() {
      // Given: 初始表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(0)
              .failedCount(0)
              .status(MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .build();

      // When: 更新进度为全部完成
      TableProgress updated = original.updateProgress(35000, 35);

      // Then: 状态应该变为 COMPLETED
      assertThat(updated.getStatus()).isEqualTo(MeshTableImportStatus.COMPLETED);
    }

    @Test
    @DisplayName("应该更新最后更新时间")
    void shouldUpdateLastUpdateTime() {
      // Given: 初始表进度（无更新时间）
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(0)
              .failedCount(0)
              .status(MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .build();

      Instant beforeUpdate = Instant.now();

      // When: 更新进度
      TableProgress updated = original.updateProgress(5000, 5);

      Instant afterUpdate = Instant.now();

      // Then: 最后更新时间应该在更新前后之间
      assertThat(updated.getLastUpdateTime()).isNotNull();
      assertThat(updated.getLastUpdateTime())
          .isBetween(beforeUpdate.minusSeconds(1), afterUpdate.plusSeconds(1));
    }

    @Test
    @DisplayName("应该保持表名和总数不变")
    void shouldKeepTableNameAndTotalCountUnchanged() {
      // Given: 初始表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(0)
              .failedCount(0)
              .status(MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .build();

      // When: 更新进度
      TableProgress updated = original.updateProgress(5000, 5);

      // Then: 表名和总数应该保持不变
      assertThat(updated.getTableName()).isEqualTo("descriptor");
      assertThat(updated.getTotalCount()).isEqualTo(35000);
    }
  }

  // ========== 增加失败数测试 ==========

  @Nested
  @DisplayName("增加失败数")
  class IncrementFailedCountTests {

    @Test
    @DisplayName("应该返回新实例当增加失败数")
    void shouldReturnNewInstanceWhenIncrementFailedCount() {
      // Given: 初始表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(5000)
              .failedCount(10)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      // When: 增加失败数
      TableProgress updated = original.incrementFailedCount(5);

      // Then: 应该返回新实例，原实例不变
      assertThat(updated).isNotSameAs(original);
      assertThat(original.getFailedCount()).isEqualTo(10);
      assertThat(updated.getFailedCount()).isEqualTo(15);
    }

    @Test
    @DisplayName("应该更新最后更新时间")
    void shouldUpdateLastUpdateTimeWhenIncrementFailed() {
      // Given: 初始表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(5000)
              .failedCount(10)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      Instant beforeUpdate = Instant.now();

      // When: 增加失败数
      TableProgress updated = original.incrementFailedCount(5);

      Instant afterUpdate = Instant.now();

      // Then: 最后更新时间应该在更新前后之间
      assertThat(updated.getLastUpdateTime()).isNotNull();
      assertThat(updated.getLastUpdateTime())
          .isBetween(beforeUpdate.minusSeconds(1), afterUpdate.plusSeconds(1));
    }

    @Test
    @DisplayName("应该保持其他字段不变")
    void shouldKeepOtherFieldsUnchanged() {
      // Given: 初始表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(5000)
              .failedCount(10)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      // When: 增加失败数
      TableProgress updated = original.incrementFailedCount(5);

      // Then: 其他字段应该保持不变
      assertThat(updated.getTableName()).isEqualTo("descriptor");
      assertThat(updated.getTotalCount()).isEqualTo(35000);
      assertThat(updated.getProcessedCount()).isEqualTo(5000);
      assertThat(updated.getStatus()).isEqualTo(MeshTableImportStatus.IN_PROGRESS);
      assertThat(updated.getLastBatchNum()).isEqualTo(5);
    }
  }

  // ========== 不可变性验证测试 ==========

  @Nested
  @DisplayName("不可变性验证")
  class ImmutabilityTests {

    @Test
    @DisplayName("值对象应该是不可变的")
    void shouldBeImmutable() {
      // Given: 创建表进度
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .totalCount(35000)
              .processedCount(5000)
              .failedCount(10)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      // When & Then: 尝试多次更新，每次都应该返回新实例
      TableProgress updated1 = progress.updateProgress(10000, 10);
      TableProgress updated2 = progress.updateProgress(15000, 15);
      TableProgress updated3 = progress.incrementFailedCount(20);

      // 验证原始实例未被修改
      assertThat(progress.getProcessedCount()).isEqualTo(5000);
      assertThat(progress.getLastBatchNum()).isEqualTo(5);
      assertThat(progress.getFailedCount()).isEqualTo(10);

      // 验证每次更新都返回不同的实例
      assertThat(updated1).isNotSameAs(progress);
      assertThat(updated2).isNotSameAs(progress);
      assertThat(updated3).isNotSameAs(progress);
      assertThat(updated1).isNotSameAs(updated2);
      assertThat(updated1).isNotSameAs(updated3);
      assertThat(updated2).isNotSameAs(updated3);
    }
  }
}
