/**
 * 游标推进器包。
 *
 * <p>本包提供游标推进功能，记录采集进度并支持增量采集。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>推进游标水位线（highWatermark）
 *   <li>记录批次进度（batchSeq、offset）
 *   <li>支持断点续传（从上次失败位置继续）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code CursorAdvancer} - 游标推进器接口
 *   <li>{@code CursorAdvancerImpl} - 游标推进器实现
 * </ul>
 *
 * <h2>游标模型</h2>
 *
 * <ul>
 *   <li>{@code provenanceCode}: 数据源代码
 *   <li>{@code operationCode}: 操作代码
 *   <li>{@code highWatermark}: 高水位线（已采集的最新时间点）
 *   <li>{@code batchSeq}: 当前批次序号
 *   <li>{@code offset}: 当前偏移量（offset-based 分页）
 *   <li>{@code cursorMark}: 游标标记（cursor-based 分页）
 *   <li>{@code updatedAt}: 更新时间
 * </ul>
 *
 * <h2>推进策略</h2>
 *
 * <h3>批次完成后推进</h3>
 *
 * <pre>
 * 1. 批次执行成功 → 推进游标
 * 2. 更新 highWatermark（如 2025-01-01 12:30:00）
 * 3. 更新 batchSeq（如 batch 2 完成 → seq=2）
 * 4. 更新 offset/cursorMark（记录分页位置）
 * </pre>
 *
 * <h3>任务完成后推进</h3>
 *
 * <pre>
 * 1. 所有批次执行成功 → 最终推进游标
 * 2. 更新 highWatermark 为窗口结束时间
 * 3. 重置 batchSeq、offset、cursorMark
 * </pre>
 *
 * <h2>断点续传</h2>
 *
 * <ul>
 *   <li><strong>场景</strong>: 任务执行失败（如网络超时、节点宕机）
 *   <li><strong>恢复</strong>: 下次执行从游标位置继续
 *       <ul>
 *         <li>读取 highWatermark → 确定起始窗口
 *         <li>读取 batchSeq → 跳过已完成的批次
 *         <li>读取 offset/cursorMark → 从上次位置继续分页
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class CursorAdvancerImpl implements CursorAdvancer {
 *     private final CursorRepository cursorRepository;
 *
 *     @Override
 *     public void advance(Long taskId, CursorPosition position) {
 *         // 1. 查询当前游标
 *         var cursor = cursorRepository.findByTask(taskId)
 *             .orElseGet(() -> Cursor.create(taskId));
 *
 *         // 2. 更新游标位置
 *         cursor.updateHighWatermark(position.getHighWatermark());
 *         cursor.updateBatchSeq(position.getBatchSeq());
 *         cursor.updateOffset(position.getOffset());
 *         cursor.updateCursorMark(position.getCursorMark());
 *
 *         // 3. 持久化
 *         cursorRepository.save(cursor);
 *
 *         log.info("Cursor advanced: taskId={}, highWatermark={}, batchSeq={}",
 *             taskId, position.getHighWatermark(), position.getBatchSeq());
 *     }
 *
 *     @Override
 *     public void advanceOnCompletion(Long taskId, Instant windowEndTime) {
 *         // 1. 查询当前游标
 *         var cursor = cursorRepository.findByTask(taskId).orElseThrow();
 *
 *         // 2. 最终推进游标
 *         cursor.updateHighWatermark(windowEndTime);
 *         cursor.resetBatchProgress();  // 重置批次进度
 *
 *         // 3. 持久化
 *         cursorRepository.save(cursor);
 *
 *         log.info("Cursor advanced on completion: taskId={}, finalWatermark={}",
 *             taskId, windowEndTime);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.cursor;
