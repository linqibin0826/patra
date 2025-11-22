package com.patra.catalog.domain.model.valueobject;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/// 失败批次值对象。
///
/// 表示导入过程中失败的批次详情,用于进度监控和错误追踪。
///
/// **设计原则**：
///
/// - 不可变性：使用 @Value 注解确保不可变对象
///   - 值语义：用于查询和展示,不涉及业务逻辑修改
///   - 领域概念：表达"失败批次"这个领域概念,而非数据库表映射
///
/// **使用场景**：
///
/// - 进度监控：展示失败批次列表
///   - 错误分析：查看失败原因和重试次数
///   - 重试决策：根据重试次数判断是否可以继续重试
///
/// @author linqibin
/// @since 0.1.0
@Value
@Builder
public class FailedBatch {

  /// 批次ID（数据库主键）
  Long batchId;

  /// 表名
  String tableName;

  /// 批次序号（从1开始）
  Integer batchNum;

  /// 失败原因
  String failureReason;

  /// 失败时间
  Instant failureTime;

  /// 重试次数
  Integer retryCount;

  /// 判断批次是否可以重试。
  ///
  /// 规则：重试次数小于3次可以重试
  ///
  /// @return true 如果可以重试
  public boolean canRetry() {
    return retryCount < 3;
  }
}
