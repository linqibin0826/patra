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
/// - ✅ getProgressPercentage() - 进度百分比计算（优先使用 actualTotalCount）
///   - ✅ updateProgress() - 更新进度（返回新实例，自动状态转换 NOT_STARTED ↔ IN_PROGRESS）
///   - ✅ markAsCompleted() - 显式标记为完成（设置 actualTotalCount 和 COMPLETED 状态）
///   - ✅ markAsFailed() - 显式标记为失败（设置 FAILED 状态）
///   - ✅ incrementFailedCount() - 增加失败数（返回新实例）
///   - ✅ 便捷判断方法 - isCompleted(), isInProgress(), isNotStarted(), isFailed()
///   - ✅ 工厂方法 - create() 创建初始状态
///   - ✅ getCountDifference() - 计算实际值与预期值的差异百分比
///   - ✅ 不可变性验证
///
/// @author linqibin
/// @since 0.1.0
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
              .expectedCount(35000)
              .actualTotalCount(null)
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
    @DisplayName("应该返回正确百分比当部分完成（使用预期值估算）")
    void shouldReturnCorrectPercentageWhenPartiallyCompleted() {
      // Given: 部分完成的表进度（actualTotalCount 未设置，使用 expectedCount 估算）
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
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
    @DisplayName("应该返回 100% 当全部完成（使用实际总数）")
    void shouldReturnHundredPercentageWhenCompleted() {
      // Given: 全部完成的表进度（已设置 actualTotalCount）
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(34800) // 实际与预期略有差异
              .processedCount(34800)
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
    @DisplayName("应该优先使用实际总数计算进度")
    void shouldUseActualTotalCountWhenAvailable() {
      // Given: 已设置实际总数的进度
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(30000) // 实际少于预期
              .processedCount(15000)
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(15)
              .build();

      // When: 计算进度百分比
      Double percentage = progress.getProgressPercentage();

      // Then: 应该基于实际总数（30000）计算，为 50%
      assertThat(percentage).isEqualTo(50.0);
    }

    @Test
    @DisplayName("应该返回 0% 当预期值为 0")
    void shouldReturnZeroPercentageWhenExpectedCountIsZero() {
      // Given: 预期值为 0 的表进度
      TableProgress progress =
          TableProgress.builder()
              .tableName("empty_table")
              .expectedCount(0)
              .actualTotalCount(null)
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
    @DisplayName("应该返回 0% 当预期值为 null")
    void shouldReturnZeroPercentageWhenExpectedCountIsNull() {
      // Given: 预期值为 null 的表进度
      TableProgress progress =
          TableProgress.builder()
              .tableName("unknown_table")
              .expectedCount(null)
              .actualTotalCount(null)
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
    @DisplayName("当已处理数超过预期值时应该限制为 100%")
    void shouldLimitToHundredPercentWhenExceedsExpected() {
      // Given: 已处理数超过预期值的进度（使用预期值估算时可能发生）
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null) // 未设置实际值，使用预期值估算
              .processedCount(36000) // 超过预期
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(36)
              .build();

      // When: 计算进度百分比
      Double percentage = progress.getProgressPercentage();

      // Then: 应该限制为 100%
      assertThat(percentage).isEqualTo(100.0);
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
              .expectedCount(35000)
              .actualTotalCount(null)
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
              .expectedCount(35000)
              .actualTotalCount(null)
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
    @DisplayName("应该抛出异常当尝试更新已完成的表")
    void shouldThrowExceptionWhenUpdatingCompletedTable() {
      // Given: 已完成的表进度
      TableProgress completed =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(34800)
              .processedCount(34800)
              .failedCount(0)
              .status(MeshTableImportStatus.COMPLETED)
              .lastBatchNum(35)
              .build();

      // When & Then: 尝试更新已完成的表应该抛出异常
      assertThatThrownBy(() -> completed.updateProgress(35000, 36))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("已完成的表不能更新进度");
    }

    @Test
    @DisplayName("应该更新最后更新时间")
    void shouldUpdateLastUpdateTime() {
      // Given: 初始表进度（无更新时间）
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
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
    @DisplayName("应该保持表名和预期值不变")
    void shouldKeepTableNameAndExpectedCountUnchanged() {
      // Given: 初始表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(0)
              .failedCount(0)
              .status(MeshTableImportStatus.NOT_STARTED)
              .lastBatchNum(0)
              .build();

      // When: 更新进度
      TableProgress updated = original.updateProgress(5000, 5);

      // Then: 表名和预期值应该保持不变
      assertThat(updated.getTableName()).isEqualTo("descriptor");
      assertThat(updated.getExpectedCount()).isEqualTo(35000);
    }
  }

  // ========== 标记为完成测试 ==========

  @Nested
  @DisplayName("标记为完成")
  class MarkAsCompletedTests {

    @Test
    @DisplayName("应该成功标记为完成并设置实际总数")
    void shouldMarkAsCompletedWithActualTotal() {
      // Given: 进行中的表进度
      TableProgress inProgress =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(30000)
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(30)
              .build();

      // When: 标记为完成（实际总数 34800）
      TableProgress completed = inProgress.markAsCompleted(34800);

      // Then: 状态变为 COMPLETED，设置实际总数和已处理数
      assertThat(completed.getStatus()).isEqualTo(MeshTableImportStatus.COMPLETED);
      assertThat(completed.getActualTotalCount()).isEqualTo(34800);
      assertThat(completed.getProcessedCount()).isEqualTo(34800);
      assertThat(completed.getExpectedCount()).isEqualTo(35000); // 预期值不变
    }

    @Test
    @DisplayName("应该返回新实例当标记为完成")
    void shouldReturnNewInstanceWhenMarkAsCompleted() {
      // Given: 进行中的表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(30000)
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(30)
              .build();

      // When: 标记为完成
      TableProgress completed = original.markAsCompleted(34800);

      // Then: 应该返回新实例，原实例不变
      assertThat(completed).isNotSameAs(original);
      assertThat(original.getStatus()).isEqualTo(MeshTableImportStatus.IN_PROGRESS);
      assertThat(original.getActualTotalCount()).isNull();
      assertThat(completed.getStatus()).isEqualTo(MeshTableImportStatus.COMPLETED);
      assertThat(completed.getActualTotalCount()).isEqualTo(34800);
    }

    @Test
    @DisplayName("应该更新最后更新时间")
    void shouldUpdateLastUpdateTimeWhenMarkAsCompleted() {
      // Given: 进行中的表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(30000)
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(30)
              .build();

      Instant beforeUpdate = Instant.now();

      // When: 标记为完成
      TableProgress completed = original.markAsCompleted(34800);

      Instant afterUpdate = Instant.now();

      // Then: 最后更新时间应该在更新前后之间
      assertThat(completed.getLastUpdateTime()).isNotNull();
      assertThat(completed.getLastUpdateTime())
          .isBetween(beforeUpdate.minusSeconds(1), afterUpdate.plusSeconds(1));
    }

    @Test
    @DisplayName("当已经完成时应该直接返回自身")
    void shouldReturnSelfWhenAlreadyCompleted() {
      // Given: 已完成的表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(34800)
              .processedCount(34800)
              .failedCount(0)
              .status(MeshTableImportStatus.COMPLETED)
              .lastBatchNum(35)
              .build();

      // When: 再次标记为完成
      TableProgress result = original.markAsCompleted(34800);

      // Then: 应该返回原实例
      assertThat(result).isSameAs(original);
    }
  }

  // ========== 标记为失败测试 ==========

  @Nested
  @DisplayName("标记为失败")
  class MarkAsFailedTests {

    @Test
    @DisplayName("应该成功标记为失败")
    void shouldMarkAsFailed() {
      // Given: 进行中的表进度
      TableProgress inProgress =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(5000)
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      // When: 标记为失败
      TableProgress failed = inProgress.markAsFailed("解析错误");

      // Then: 状态变为 FAILED
      assertThat(failed.getStatus()).isEqualTo(MeshTableImportStatus.FAILED);
    }

    @Test
    @DisplayName("应该返回新实例当标记为失败")
    void shouldReturnNewInstanceWhenMarkAsFailed() {
      // Given: 进行中的表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(5000)
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      // When: 标记为失败
      TableProgress failed = original.markAsFailed("解析错误");

      // Then: 应该返回新实例，原实例不变
      assertThat(failed).isNotSameAs(original);
      assertThat(original.getStatus()).isEqualTo(MeshTableImportStatus.IN_PROGRESS);
      assertThat(failed.getStatus()).isEqualTo(MeshTableImportStatus.FAILED);
    }

    @Test
    @DisplayName("应该更新最后更新时间")
    void shouldUpdateLastUpdateTimeWhenMarkAsFailed() {
      // Given: 进行中的表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(5000)
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      Instant beforeUpdate = Instant.now();

      // When: 标记为失败
      TableProgress failed = original.markAsFailed("解析错误");

      Instant afterUpdate = Instant.now();

      // Then: 最后更新时间应该在更新前后之间
      assertThat(failed.getLastUpdateTime()).isNotNull();
      assertThat(failed.getLastUpdateTime())
          .isBetween(beforeUpdate.minusSeconds(1), afterUpdate.plusSeconds(1));
    }

    @Test
    @DisplayName("应该保持其他字段不变")
    void shouldKeepOtherFieldsUnchangedWhenMarkAsFailed() {
      // Given: 进行中的表进度
      TableProgress original =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(5000)
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      // When: 标记为失败
      TableProgress failed = original.markAsFailed("解析错误");

      // Then: 其他字段应该保持不变
      assertThat(failed.getTableName()).isEqualTo("descriptor");
      assertThat(failed.getExpectedCount()).isEqualTo(35000);
      assertThat(failed.getProcessedCount()).isEqualTo(5000);
      assertThat(failed.getFailedCount()).isEqualTo(0);
      assertThat(failed.getLastBatchNum()).isEqualTo(5);
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
              .expectedCount(35000)
              .actualTotalCount(null)
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
              .expectedCount(35000)
              .actualTotalCount(null)
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
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(5000)
              .failedCount(10)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      // When: 增加失败数
      TableProgress updated = original.incrementFailedCount(5);

      // Then: 其他字段应该保持不变
      assertThat(updated.getTableName()).isEqualTo("descriptor");
      assertThat(updated.getExpectedCount()).isEqualTo(35000);
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
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(5000)
              .failedCount(10)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      // When & Then: 尝试多次更新，每次都应该返回新实例
      TableProgress updated1 = progress.updateProgress(10000, 10);
      TableProgress updated2 = progress.updateProgress(15000, 15);
      TableProgress updated3 = progress.incrementFailedCount(20);
      TableProgress updated4 = progress.markAsFailed("测试错误");

      // 验证原始实例未被修改
      assertThat(progress.getProcessedCount()).isEqualTo(5000);
      assertThat(progress.getLastBatchNum()).isEqualTo(5);
      assertThat(progress.getFailedCount()).isEqualTo(10);
      assertThat(progress.getStatus()).isEqualTo(MeshTableImportStatus.IN_PROGRESS);

      // 验证每次更新都返回不同的实例
      assertThat(updated1).isNotSameAs(progress);
      assertThat(updated2).isNotSameAs(progress);
      assertThat(updated3).isNotSameAs(progress);
      assertThat(updated4).isNotSameAs(progress);
      assertThat(updated1).isNotSameAs(updated2);
      assertThat(updated1).isNotSameAs(updated3);
      assertThat(updated1).isNotSameAs(updated4);
      assertThat(updated2).isNotSameAs(updated3);
      assertThat(updated2).isNotSameAs(updated4);
      assertThat(updated3).isNotSameAs(updated4);
    }
  }

  // ========== 便捷方法测试 ==========

  @Nested
  @DisplayName("便捷判断方法")
  class ConvenienceMethodTests {

    @Test
    @DisplayName("isCompleted() 应该正确判断完成状态")
    void shouldCorrectlyCheckIfCompleted() {
      // Given: 不同状态的进度
      TableProgress notStarted = TableProgress.create("descriptor", 35000);
      TableProgress inProgress = notStarted.updateProgress(5000, 5);
      TableProgress completed = inProgress.markAsCompleted(34800);
      TableProgress failed = inProgress.markAsFailed("错误");

      // Then: 只有 COMPLETED 状态返回 true
      assertThat(notStarted.isCompleted()).isFalse();
      assertThat(inProgress.isCompleted()).isFalse();
      assertThat(completed.isCompleted()).isTrue();
      assertThat(failed.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("isInProgress() 应该正确判断进行中状态")
    void shouldCorrectlyCheckIfInProgress() {
      // Given: 不同状态的进度
      TableProgress notStarted = TableProgress.create("descriptor", 35000);
      TableProgress inProgress = notStarted.updateProgress(5000, 5);
      TableProgress completed = inProgress.markAsCompleted(34800);
      TableProgress failed = inProgress.markAsFailed("错误");

      // Then: 只有 IN_PROGRESS 状态返回 true
      assertThat(notStarted.isInProgress()).isFalse();
      assertThat(inProgress.isInProgress()).isTrue();
      assertThat(completed.isInProgress()).isFalse();
      assertThat(failed.isInProgress()).isFalse();
    }

    @Test
    @DisplayName("isNotStarted() 应该正确判断未开始状态")
    void shouldCorrectlyCheckIfNotStarted() {
      // Given: 不同状态的进度
      TableProgress notStarted = TableProgress.create("descriptor", 35000);
      TableProgress inProgress = notStarted.updateProgress(5000, 5);
      TableProgress completed = inProgress.markAsCompleted(34800);
      TableProgress failed = inProgress.markAsFailed("错误");

      // Then: 只有 NOT_STARTED 状态返回 true
      assertThat(notStarted.isNotStarted()).isTrue();
      assertThat(inProgress.isNotStarted()).isFalse();
      assertThat(completed.isNotStarted()).isFalse();
      assertThat(failed.isNotStarted()).isFalse();
    }

    @Test
    @DisplayName("isFailed() 应该正确判断失败状态")
    void shouldCorrectlyCheckIfFailed() {
      // Given: 不同状态的进度
      TableProgress notStarted = TableProgress.create("descriptor", 35000);
      TableProgress inProgress = notStarted.updateProgress(5000, 5);
      TableProgress completed = inProgress.markAsCompleted(34800);
      TableProgress failed = inProgress.markAsFailed("错误");

      // Then: 只有 FAILED 状态返回 true
      assertThat(notStarted.isFailed()).isFalse();
      assertThat(inProgress.isFailed()).isFalse();
      assertThat(completed.isFailed()).isFalse();
      assertThat(failed.isFailed()).isTrue();
    }
  }

  // ========== 工厂方法测试 ==========

  @Nested
  @DisplayName("工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("create() 应该创建初始状态的进度")
    void shouldCreateInitialProgress() {
      // When: 使用工厂方法创建
      TableProgress progress = TableProgress.create("descriptor", 35000);

      // Then: 应该是初始状态
      assertThat(progress.getTableName()).isEqualTo("descriptor");
      assertThat(progress.getExpectedCount()).isEqualTo(35000);
      assertThat(progress.getActualTotalCount()).isNull();
      assertThat(progress.getProcessedCount()).isEqualTo(0);
      assertThat(progress.getFailedCount()).isEqualTo(0);
      assertThat(progress.getStatus()).isEqualTo(MeshTableImportStatus.NOT_STARTED);
      assertThat(progress.getLastBatchNum()).isEqualTo(0);
      assertThat(progress.getLastUpdateTime()).isNotNull();
    }
  }

  // ========== 数量差异计算测试 ==========

  @Nested
  @DisplayName("数量差异计算")
  class CountDifferenceTests {

    @Test
    @DisplayName("应该正确计算实际值多于预期值的差异")
    void shouldCalculatePositiveDifference() {
      // Given: 实际值多于预期值
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(35700) // 多了 700
              .processedCount(35700)
              .failedCount(0)
              .status(MeshTableImportStatus.COMPLETED)
              .lastBatchNum(36)
              .build();

      // When: 计算差异
      Double difference = progress.getCountDifference();

      // Then: 应该为正值，约 2%
      assertThat(difference).isNotNull().isCloseTo(2.0, within(0.01));
    }

    @Test
    @DisplayName("应该正确计算实际值少于预期值的差异")
    void shouldCalculateNegativeDifference() {
      // Given: 实际值少于预期值
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(34300) // 少了 700
              .processedCount(34300)
              .failedCount(0)
              .status(MeshTableImportStatus.COMPLETED)
              .lastBatchNum(34)
              .build();

      // When: 计算差异
      Double difference = progress.getCountDifference();

      // Then: 应该为负值，约 -2%
      assertThat(difference).isNotNull().isCloseTo(-2.0, within(0.01));
    }

    @Test
    @DisplayName("当实际总数未设置时应该返回 null")
    void shouldReturnNullWhenActualTotalCountIsNull() {
      // Given: 实际总数未设置
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(35000)
              .actualTotalCount(null)
              .processedCount(5000)
              .failedCount(0)
              .status(MeshTableImportStatus.IN_PROGRESS)
              .lastBatchNum(5)
              .build();

      // When: 计算差异
      Double difference = progress.getCountDifference();

      // Then: 应该返回 null
      assertThat(difference).isNull();
    }

    @Test
    @DisplayName("当预期值为 null 时应该返回 null")
    void shouldReturnNullWhenExpectedCountIsNull() {
      // Given: 预期值为 null
      TableProgress progress =
          TableProgress.builder()
              .tableName("descriptor")
              .expectedCount(null)
              .actualTotalCount(35000)
              .processedCount(35000)
              .failedCount(0)
              .status(MeshTableImportStatus.COMPLETED)
              .lastBatchNum(35)
              .build();

      // When: 计算差异
      Double difference = progress.getCountDifference();

      // Then: 应该返回 null
      assertThat(difference).isNull();
    }
  }
}
