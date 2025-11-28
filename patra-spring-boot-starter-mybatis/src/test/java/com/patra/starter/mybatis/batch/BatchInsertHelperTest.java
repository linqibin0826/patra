package com.patra.starter.mybatis.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.mybatis.batch.BatchInsertHelper.BatchError;
import com.patra.starter.mybatis.batch.BatchInsertHelper.BatchInsertResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// BatchInsertHelper 单元测试。
///
/// 测试分片批量插入的边界条件、分片逻辑和结果统计。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("BatchInsertHelper 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class BatchInsertHelperTest {

  @Nested
  @DisplayName("batchInsert 空输入处理")
  class EmptyInputTests {

    @Test
    @DisplayName("空列表应返回 empty 结果")
    void batchInsert_emptyList_shouldReturnEmptyResult() {
      // Arrange
      List<Integer> emptyList = Collections.emptyList();
      Function<List<Integer>, Integer> inserter = list -> list.size();

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(emptyList, inserter);

      // Assert
      assertThat(result.totalCount()).isZero();
      assertThat(result.successCount()).isZero();
      assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("null 列表应返回 empty 结果")
    void batchInsert_nullList_shouldReturnEmptyResult() {
      // Arrange
      Function<List<Integer>, Integer> inserter = list -> list.size();

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(null, inserter);

      // Assert
      assertThat(result.totalCount()).isZero();
      assertThat(result.successCount()).isZero();
      assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("空列表的 isAllSuccess 应为 true")
    void batchInsert_emptyList_isAllSuccessShouldBeTrue() {
      // Arrange
      List<Integer> emptyList = Collections.emptyList();
      Function<List<Integer>, Integer> inserter = list -> list.size();

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(emptyList, inserter);

      // Assert
      assertThat(result.isAllSuccess()).isTrue();
      assertThat(result.hasErrors()).isFalse();
    }
  }

  @Nested
  @DisplayName("batchInsert 批次大小处理")
  class BatchSizeTests {

    @Test
    @DisplayName("batchSize 为 0 应使用默认值 1000")
    void batchInsert_zeroBatchSize_shouldUseDefaultValue() {
      // Arrange
      List<Integer> dataList = createDataList(100);
      AtomicInteger callCount = new AtomicInteger(0);
      Function<List<Integer>, Integer> inserter =
          list -> {
            callCount.incrementAndGet();
            return list.size();
          };

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(dataList, 0, inserter);

      // Assert - 100 条数据，默认 1000 批次，应只调用 1 次
      assertThat(callCount.get()).isEqualTo(1);
      assertThat(result.successCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("batchSize 为负数应使用默认值 1000")
    void batchInsert_negativeBatchSize_shouldUseDefaultValue() {
      // Arrange
      List<Integer> dataList = createDataList(100);
      AtomicInteger callCount = new AtomicInteger(0);
      Function<List<Integer>, Integer> inserter =
          list -> {
            callCount.incrementAndGet();
            return list.size();
          };

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(dataList, -10, inserter);

      // Assert - 100 条数据，默认 1000 批次，应只调用 1 次
      assertThat(callCount.get()).isEqualTo(1);
      assertThat(result.successCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("自定义 batchSize 应正确分片")
    void batchInsert_customBatchSize_shouldSplitCorrectly() {
      // Arrange
      List<Integer> dataList = createDataList(100);
      AtomicInteger callCount = new AtomicInteger(0);
      Function<List<Integer>, Integer> inserter =
          list -> {
            callCount.incrementAndGet();
            return list.size();
          };

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(dataList, 30, inserter);

      // Assert - 100 条数据，30 批次，应调用 4 次（30+30+30+10）
      assertThat(callCount.get()).isEqualTo(4);
      assertThat(result.successCount()).isEqualTo(100);
    }
  }

  @Nested
  @DisplayName("batchInsert 分片逻辑")
  class ShardingLogicTests {

    @Test
    @DisplayName("数据量小于批次大小应单批处理")
    void batchInsert_lessThanBatchSize_shouldProcessInSingleBatch() {
      // Arrange
      List<Integer> dataList = createDataList(50);
      List<Integer> batchSizes = new ArrayList<>();
      Function<List<Integer>, Integer> inserter =
          list -> {
            batchSizes.add(list.size());
            return list.size();
          };

      // Act
      BatchInsertHelper.batchInsert(dataList, 100, inserter);

      // Assert
      assertThat(batchSizes).containsExactly(50);
    }

    @Test
    @DisplayName("数据量等于批次大小应单批处理")
    void batchInsert_equalToBatchSize_shouldProcessInSingleBatch() {
      // Arrange
      List<Integer> dataList = createDataList(100);
      List<Integer> batchSizes = new ArrayList<>();
      Function<List<Integer>, Integer> inserter =
          list -> {
            batchSizes.add(list.size());
            return list.size();
          };

      // Act
      BatchInsertHelper.batchInsert(dataList, 100, inserter);

      // Assert
      assertThat(batchSizes).containsExactly(100);
    }

    @Test
    @DisplayName("数据量略大于批次大小应分两批")
    void batchInsert_slightlyMoreThanBatchSize_shouldProcessInTwoBatches() {
      // Arrange
      List<Integer> dataList = createDataList(101);
      List<Integer> batchSizes = new ArrayList<>();
      Function<List<Integer>, Integer> inserter =
          list -> {
            batchSizes.add(list.size());
            return list.size();
          };

      // Act
      BatchInsertHelper.batchInsert(dataList, 100, inserter);

      // Assert
      assertThat(batchSizes).containsExactly(100, 1);
    }

    @Test
    @DisplayName("大数据量应正确计算批次数")
    void batchInsert_largeDataSet_shouldCalculateBatchesCorrectly() {
      // Arrange
      List<Integer> dataList = createDataList(2500);
      AtomicInteger callCount = new AtomicInteger(0);
      Function<List<Integer>, Integer> inserter =
          list -> {
            callCount.incrementAndGet();
            return list.size();
          };

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(dataList, 1000, inserter);

      // Assert - 2500 / 1000 = 3 批（1000+1000+500）
      assertThat(callCount.get()).isEqualTo(3);
      assertThat(result.successCount()).isEqualTo(2500);
    }

    @Test
    @DisplayName("非整除场景最后一批应包含余数")
    void batchInsert_nonDivisible_lastBatchShouldContainRemainder() {
      // Arrange
      List<Integer> dataList = createDataList(250);
      List<Integer> batchSizes = new ArrayList<>();
      Function<List<Integer>, Integer> inserter =
          list -> {
            batchSizes.add(list.size());
            return list.size();
          };

      // Act
      BatchInsertHelper.batchInsert(dataList, 100, inserter);

      // Assert - 250 / 100 = 2 批 + 余数 50
      assertThat(batchSizes).containsExactly(100, 100, 50);
    }
  }

  @Nested
  @DisplayName("batchInsert 结果统计")
  class ResultStatisticsTests {

    @Test
    @DisplayName("全部成功应返回正确的 successCount")
    void batchInsert_allSuccess_shouldReturnCorrectSuccessCount() {
      // Arrange
      List<Integer> dataList = createDataList(150);
      Function<List<Integer>, Integer> inserter = List::size;

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(dataList, 50, inserter);

      // Assert
      assertThat(result.totalCount()).isEqualTo(150);
      assertThat(result.successCount()).isEqualTo(150);
    }

    @Test
    @DisplayName("全部成功的 hasErrors 应为 false")
    void batchInsert_allSuccess_hasErrorsShouldBeFalse() {
      // Arrange
      List<Integer> dataList = createDataList(100);
      Function<List<Integer>, Integer> inserter = List::size;

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(dataList, 50, inserter);

      // Assert
      assertThat(result.hasErrors()).isFalse();
      assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("部分失败应收集错误信息")
    void batchInsert_partialFailure_shouldCollectErrors() {
      // Arrange
      List<Integer> dataList = createDataList(150);
      AtomicInteger batchCounter = new AtomicInteger(0);
      Function<List<Integer>, Integer> inserter =
          list -> {
            int batch = batchCounter.incrementAndGet();
            if (batch == 2) {
              throw new RuntimeException("模拟批次 2 失败");
            }
            return list.size();
          };

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(dataList, 50, inserter);

      // Assert
      assertThat(result.totalCount()).isEqualTo(150);
      assertThat(result.successCount()).isEqualTo(100); // 批次 1 和 3 成功
      assertThat(result.errors()).hasSize(1);
    }

    @Test
    @DisplayName("部分失败的 hasErrors 应为 true")
    void batchInsert_partialFailure_hasErrorsShouldBeTrue() {
      // Arrange
      List<Integer> dataList = createDataList(100);
      AtomicInteger batchCounter = new AtomicInteger(0);
      Function<List<Integer>, Integer> inserter =
          list -> {
            if (batchCounter.incrementAndGet() == 1) {
              throw new RuntimeException("模拟失败");
            }
            return list.size();
          };

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(dataList, 50, inserter);

      // Assert
      assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("errors 应包含失败批次的异常信息")
    void batchInsert_failure_errorsShouldContainExceptionInfo() {
      // Arrange
      List<Integer> dataList = createDataList(100);
      RuntimeException expectedException = new RuntimeException("测试异常");
      Function<List<Integer>, Integer> inserter =
          list -> {
            throw expectedException;
          };

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(dataList, 50, inserter);

      // Assert
      assertThat(result.errors()).hasSize(2); // 两批都失败
      BatchError<Integer> firstError = result.errors().get(0);
      assertThat(firstError.batchNumber()).isEqualTo(1);
      assertThat(firstError.startIndex()).isEqualTo(0);
      assertThat(firstError.endIndex()).isEqualTo(50);
      assertThat(firstError.exception()).isEqualTo(expectedException);
      assertThat(firstError.failedData()).hasSize(50);
    }
  }

  @Nested
  @DisplayName("BatchInsertResult")
  class BatchInsertResultTests {

    @Test
    @DisplayName("empty 结果的各属性验证")
    void empty_shouldHaveCorrectProperties() {
      // Act
      BatchInsertResult<String> result = BatchInsertResult.empty();

      // Assert
      assertThat(result.totalCount()).isZero();
      assertThat(result.successCount()).isZero();
      assertThat(result.errors()).isEmpty();
      assertThat(result.hasErrors()).isFalse();
      assertThat(result.isAllSuccess()).isTrue();
      assertThat(result.getFailedCount()).isZero();
    }

    @Test
    @DisplayName("isAllSuccess 与 hasErrors 互斥")
    void isAllSuccess_andHasErrors_shouldBeMutuallyExclusive() {
      // Arrange - 成功场景
      BatchInsertResult<String> successResult = new BatchInsertResult<>(100, 100, List.of());

      // Assert
      assertThat(successResult.isAllSuccess()).isTrue();
      assertThat(successResult.hasErrors()).isFalse();

      // Arrange - 失败场景
      BatchError<String> error = new BatchError<>(1, 0, 50, List.of("a"), new RuntimeException());
      BatchInsertResult<String> failureResult = new BatchInsertResult<>(100, 50, List.of(error));

      // Assert
      assertThat(failureResult.isAllSuccess()).isFalse();
      assertThat(failureResult.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("totalCount 应等于原始数据量")
    void totalCount_shouldEqualOriginalDataSize() {
      // Arrange
      List<Integer> dataList = createDataList(237);
      Function<List<Integer>, Integer> inserter = List::size;

      // Act
      BatchInsertResult<Integer> result = BatchInsertHelper.batchInsert(dataList, 50, inserter);

      // Assert
      assertThat(result.totalCount()).isEqualTo(237);
    }

    @Test
    @DisplayName("getFailedCount 应返回失败记录数")
    void getFailedCount_shouldReturnCorrectValue() {
      // Arrange
      BatchInsertResult<String> result = new BatchInsertResult<>(100, 75, List.of());

      // Act & Assert
      assertThat(result.getFailedCount()).isEqualTo(25);
    }
  }

  @Nested
  @DisplayName("DEFAULT_BATCH_SIZE 常量")
  class DefaultBatchSizeTests {

    @Test
    @DisplayName("默认批次大小应为 1000")
    void defaultBatchSize_shouldBe1000() {
      assertThat(BatchInsertHelper.DEFAULT_BATCH_SIZE).isEqualTo(1000);
    }

    @Test
    @DisplayName("无参版本应使用默认批次大小")
    void batchInsert_withoutBatchSize_shouldUseDefault() {
      // Arrange
      List<Integer> dataList = createDataList(1500);
      AtomicInteger callCount = new AtomicInteger(0);
      Function<List<Integer>, Integer> inserter =
          list -> {
            callCount.incrementAndGet();
            return list.size();
          };

      // Act
      BatchInsertHelper.batchInsert(dataList, inserter);

      // Assert - 1500 / 1000 = 2 批
      assertThat(callCount.get()).isEqualTo(2);
    }
  }

  /// 创建测试数据列表。
  private List<Integer> createDataList(int size) {
    return IntStream.range(0, size).boxed().toList();
  }
}
