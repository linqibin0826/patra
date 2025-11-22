package com.patra.ingest.domain.model.vo.plan;

/// 任务调度器上下文值对象,表示附加到任务的调度上下文。
/// 
/// 存储用于分布式追踪的跨组件关联ID。
/// 
/// 不可变性:以`with*`为前缀的便捷方法返回新实例
/// 
/// 使用场景:在分布式系统中追踪任务执行链路
/// 
/// @param correlationId 跨系统追踪/日志标识符
public record TaskSchedulerContext(String correlationId) {

  /// 创建空调度器上下文(correlationId为`null`)。
/// 
/// @return 空调度器上下文
  public static TaskSchedulerContext empty() {
    return new TaskSchedulerContext(null);
  }

  /// 派生具有指定`correlationId`的新上下文。
/// 
/// @param corrId 关联ID
/// @return 新的调度器上下文
  public TaskSchedulerContext withCorrelation(String corrId) {
    return new TaskSchedulerContext(corrId);
  }
}
