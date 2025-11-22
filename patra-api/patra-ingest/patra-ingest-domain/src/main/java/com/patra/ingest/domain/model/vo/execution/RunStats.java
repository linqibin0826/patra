package com.patra.ingest.domain.model.vo.execution;

/// 运行统计值对象,表示任务运行期间产生的聚合统计数据。
/// 
/// 跟踪获取的原始记录数、成功更新插入的记录数、失败数和批次/页数。
/// 
/// 不可变性:通过值语义比较相等性
/// 
/// 业务约束:
/// 
/// - 所有字段都是非负长整型
/// 
/// 使用场景:汇总任务运行的各项统计指标
/// 
/// @param fetched 获取的记录数
/// @param upserted 成功更新插入的记录数
/// @param failed 失败的记录数
/// @param pages 处理的页数/批次数
public record RunStats(long fetched, long upserted, long failed, long pages) {
  /// 创建空统计记录(所有计数器为零)。
/// 
/// @return 空统计记录
  public static RunStats empty() {
    return new RunStats(0, 0, 0, 0);
  }

  /// 合并此统计快照与另一个不可变增量。
/// 
/// @param delta 要添加的增量统计
/// @return 新的聚合统计实例
  public RunStats add(RunStats delta) {
    return new RunStats(
        fetched + delta.fetched,
        upserted + delta.upserted,
        failed + delta.failed,
        pages + delta.pages);
  }
}
