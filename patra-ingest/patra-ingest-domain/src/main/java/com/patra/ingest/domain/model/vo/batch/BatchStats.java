package com.patra.ingest.domain.model.vo.batch;

/// 单个批次执行统计 Value Object。
/// 
/// **业务含义:** 记录批次处理的统计信息,目前包含处理记录数。
/// 
/// **不变性:** `recordCount` 由调用方保证非负,此类不进行验证。
/// 
/// @param recordCount 处理的记录数(调用方保证 >= 0)
/// @author linqibin
/// @since 0.1.0
public record BatchStats(int recordCount) {
  /// 工厂方法: 创建批次统计对象。
  public static BatchStats of(int count) {
    return new BatchStats(count);
  }
}
