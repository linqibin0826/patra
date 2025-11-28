package com.patra.starter.mybatis.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/// 批量插入辅助工具。
///
/// 提供分片批量插入功能，自动处理大数据量场景，避免超出 MySQL 的 `max_allowed_packet` 限制。
///
/// ## 使用示例
///
/// ```java
/// // 简化版：使用默认批次大小（1000）
/// BatchInsertHelper.batchInsert(dataList, mapper::insertBatchSomeColumn);
///
/// // 完整版：自定义批次大小
/// BatchInsertHelper.batchInsert(dataList, 500, mapper::insertBatchSomeColumn);
/// ```
///
/// ## 注意事项
///
/// - 每个批次作为独立操作执行，失败时仅影响当前批次
/// - 建议在 Application 层的 `@Transactional` 方法中调用
/// - 默认批次大小为 1000，可通过配置文件或参数调整
///
/// @author Patra Team
/// @since 0.1.0
@Slf4j
public final class BatchInsertHelper {

  /// 默认批次大小（1000 条/批）。
  public static final int DEFAULT_BATCH_SIZE = 1000;

  private BatchInsertHelper() {
    // 工具类禁止实例化
  }

  /// 分片批量插入（使用默认批次大小）。
  ///
  /// 将数据列表按默认批次大小（1000）分片，逐批执行插入操作。
  ///
  /// @param dataList 待插入数据列表
  /// @param batchInserter 批量插入函数，通常为 `mapper::insertBatchSomeColumn`
  /// @param <T> 数据类型
  /// @return 批量插入结果
  public static <T> BatchInsertResult<T> batchInsert(
      List<T> dataList, Function<List<T>, Integer> batchInserter) {
    return batchInsert(dataList, DEFAULT_BATCH_SIZE, batchInserter);
  }

  /// 分片批量插入（自定义批次大小）。
  ///
  /// 将数据列表按指定批次大小分片，逐批执行插入操作。
  ///
  /// @param dataList 待插入数据列表
  /// @param batchSize 每批次大小（建议 500-5000）
  /// @param batchInserter 批量插入函数，通常为 `mapper::insertBatchSomeColumn`
  /// @param <T> 数据类型
  /// @return 批量插入结果，包含成功数、失败批次等信息
  public static <T> BatchInsertResult<T> batchInsert(
      List<T> dataList, int batchSize, Function<List<T>, Integer> batchInserter) {

    if (dataList == null || dataList.isEmpty()) {
      return BatchInsertResult.empty();
    }

    int totalSize = dataList.size();
    int actualBatchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
    int successCount = 0;
    List<BatchError<T>> errors = new ArrayList<>();

    log.info("开始批量插入，总数据量: {}，批次大小: {}", totalSize, actualBatchSize);

    for (int i = 0; i < totalSize; i += actualBatchSize) {
      int end = Math.min(i + actualBatchSize, totalSize);
      List<T> batch = dataList.subList(i, end);
      int batchNumber = (i / actualBatchSize) + 1;

      try {
        int affected = batchInserter.apply(batch);
        successCount += affected;
        log.debug("批次 {} [{}-{}] 插入成功，影响行数: {}", batchNumber, i, end, affected);
      } catch (Exception e) {
        log.error("批次 {} [{}-{}] 插入失败: {}", batchNumber, i, end, e.getMessage());
        errors.add(new BatchError<>(batchNumber, i, end, batch, e));
      }
    }

    BatchInsertResult<T> result = new BatchInsertResult<>(totalSize, successCount, errors);

    if (result.hasErrors()) {
      log.warn("批量插入完成，存在失败批次：成功 {} / 总计 {}，失败批次数: {}", successCount, totalSize, errors.size());
    } else {
      log.info("批量插入完成：成功 {} 条", successCount);
    }

    return result;
  }

  /// 批量插入结果。
  ///
  /// @param totalCount 总记录数
  /// @param successCount 成功插入数
  /// @param errors 失败批次列表
  /// @param <T> 数据类型
  public record BatchInsertResult<T>(int totalCount, int successCount, List<BatchError<T>> errors) {

    /// 创建空结果。
    public static <T> BatchInsertResult<T> empty() {
      return new BatchInsertResult<>(0, 0, List.of());
    }

    /// 是否存在失败批次。
    public boolean hasErrors() {
      return errors != null && !errors.isEmpty();
    }

    /// 获取失败记录数。
    public int getFailedCount() {
      return totalCount - successCount;
    }

    /// 是否全部成功。
    public boolean isAllSuccess() {
      return !hasErrors() && successCount == totalCount;
    }
  }

  /// 批次错误信息。
  ///
  /// @param batchNumber 批次编号（从 1 开始）
  /// @param startIndex 起始索引
  /// @param endIndex 结束索引（不含）
  /// @param failedData 失败的数据列表
  /// @param exception 异常信息
  /// @param <T> 数据类型
  public record BatchError<T>(
      int batchNumber, int startIndex, int endIndex, List<T> failedData, Exception exception) {}
}
