/// 游标推进器包。
/// 
/// 本包提供游标推进功能，记录采集进度并支持增量采集。
/// 
/// ## 职责
/// 
/// - 推进游标水位线（highWatermark）
///   - 记录批次进度（batchSeq、offset）
///   - 支持断点续传（从上次失败位置继续）
/// 
/// ## 核心组件
/// 
/// - `CursorAdvancer` - 游标推进器接口
///   - `CursorAdvancerImpl` - 游标推进器实现
/// 
/// ## 游标模型
/// 
/// - `provenanceCode`: 数据源代码
///   - `operationCode`: 操作代码
///   - `highWatermark`: 高水位线（已采集的最新时间点）
///   - `batchSeq`: 当前批次序号
///   - `offset`: 当前偏移量（offset-based 分页）
///   - `cursorMark`: 游标标记（cursor-based 分页）
///   - `updatedAt`: 更新时间
/// 
/// ## 推进策略
/// 
/// ### 批次完成后推进
/// 
/// ```
/// 
/// 1. 批次执行成功 → 推进游标
/// 2. 更新 highWatermark（如 2025-01-01 12:30:00）
/// 3. 更新 batchSeq（如 batch 2 完成 → seq=2）
/// 4. 更新 offset/cursorMark（记录分页位置）
/// 
/// ```
/// 
/// ### 任务完成后推进
/// 
/// ```
/// 
/// 1. 所有批次执行成功 → 最终推进游标
/// 2. 更新 highWatermark 为窗口结束时间
/// 3. 重置 batchSeq、offset、cursorMark
/// 
/// ```
/// 
/// ## 断点续传
/// 
/// - **场景**: 任务执行失败（如网络超时、节点宕机）
///   - **恢复**: 下次执行从游标位置继续
///       
/// - 读取 highWatermark → 确定起始窗口
///         - 读取 batchSeq → 跳过已完成的批次
///         - 读取 offset/cursorMark → 从上次位置继续分页
/// 
/// ## 使用示例
/// 
/// ```java
/// @Component
/// @RequiredArgsConstructor
/// public class CursorAdvancerImpl implements CursorAdvancer {
///     private final CursorRepository cursorRepository;
/// 
///     @Override
///     public void advance(Long taskId, CursorPosition position) {
///         // 1. 查询当前游标
///         var cursor = cursorRepository.findByTask(taskId)
///             .orElseGet(() -> Cursor.create(taskId));
/// 
///         // 2. 更新游标位置
///         cursor.updateHighWatermark(position.getHighWatermark());
///         cursor.updateBatchSeq(position.getBatchSeq());
///         cursor.updateOffset(position.getOffset());
///         cursor.updateCursorMark(position.getCursorMark());
/// 
///         // 3. 持久化
///         cursorRepository.save(cursor);
/// 
///         log.info("Cursor advanced: taskId={, highWatermark={, batchSeq={",
///             taskId, position.getHighWatermark(), position.getBatchSeq());
/// 
///     @Override
///     public void advanceOnCompletion(Long taskId, Instant windowEndTime) {
///         // 1. 查询当前游标
///         var cursor = cursorRepository.findByTask(taskId).orElseThrow();
/// 
///         // 2. 最终推进游标
///         cursor.updateHighWatermark(windowEndTime);
///         cursor.resetBatchProgress();  // 重置批次进度
/// 
///         // 3. 持久化
///         cursorRepository.save(cursor);
/// 
///         log.info("Cursor advanced on completion: taskId={, finalWatermark={",
///             taskId, windowEndTime);
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.execution.cursor;
