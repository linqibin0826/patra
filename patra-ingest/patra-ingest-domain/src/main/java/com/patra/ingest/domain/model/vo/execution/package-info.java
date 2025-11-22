/// 任务执行值对象。
/// 
/// 包含与任务执行生命周期相关的值对象:
/// 
/// - {@link com.patra.ingest.domain.model.vo.execution.ExecutionContext} - 带配置快照的执行上下文
///   - {@link com.patra.ingest.domain.model.vo.execution.ExecutionTimeline} - 执行时间线追踪
///   - {@link com.patra.ingest.domain.model.vo.execution.RunContext} - 任务运行上下文
///   - {@link com.patra.ingest.domain.model.vo.execution.RunStats} - 任务运行统计信息
///   - {@link com.patra.ingest.domain.model.vo.execution.TaskParams} - 任务参数
///   - {@link com.patra.ingest.domain.model.vo.execution.TaskReadyMessage} - 用于队列的任务就绪消息
///   - {@link com.patra.ingest.domain.model.vo.execution.TaskRunCheckpoint} - 用于恢复的任务运行检查点
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.ingest.domain.model.vo.execution;
