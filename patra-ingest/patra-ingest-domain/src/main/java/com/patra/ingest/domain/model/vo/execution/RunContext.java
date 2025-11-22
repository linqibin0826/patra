package com.patra.ingest.domain.model.vo.execution;

/// 运行上下文值对象,表示单个任务运行的上下文元数据.
/// 
/// 类似于{@link TaskSchedulerContext}但作用域限于单个运行.
/// 
/// - `correlationId`:用于分布式追踪的跨系统关联ID
/// 
public record RunContext(String correlationId) {

  /// 创建空运行上下文.
  public static RunContext empty() {
    return new RunContext(null);
  }

  /// 派生具有指定`correlationId`的新上下文.
  public RunContext withCorrelation(String corrId) {
    return new RunContext(corrId);
  }
}
