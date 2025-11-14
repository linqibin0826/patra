/**
 * 规划窗口解析器包。
 *
 * <p>本包提供规划窗口的解析和计算逻辑，根据游标水位线和配置确定采集范围。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>根据游标水位线计算规划窗口起始点
 *   <li>根据配置和当前时间计算规划窗口结束点
 *   <li>支持全量采集和增量采集的窗口计算
 *   <li>提供窗口合法性检查
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code PlanningWindowResolver} - 窗口解析器接口
 *   <li>{@code PlanningWindowResolverImpl} - 窗口解析器实现
 *   <li>{@code PlanningWindowSupport} - 窗口解析支持工具
 * </ul>
 *
 * <h2>解析策略</h2>
 *
 * <h3>增量采集</h3>
 *
 * <pre>
 * 输入：
 *   - 游标水位线: 2025-01-01 12:00:00
 *   - 当前时间: 2025-01-02 14:00:00
 *   - 配置: 每次采集 1 小时
 *
 * 输出：
 *   - windowFrom: 2025-01-01 12:00:00 (游标水位线)
 *   - windowTo: 2025-01-01 13:00:00 (水位线 + 1 小时)
 * </pre>
 *
 * <h3>全量采集</h3>
 *
 * <pre>
 * 输入：
 *   - 游标水位线: null (首次采集)
 *   - 当前时间: 2025-01-02 14:00:00
 *   - 配置: 回溯 30 天
 *
 * 输出：
 *   - windowFrom: 2025-01-01 00:00:00 (当前时间 - 30 天)
 *   - windowTo: 2025-01-02 14:00:00 (当前时间)
 * </pre>
 *
 * <h2>边界处理</h2>
 *
 * <ul>
 *   <li><strong>未来窗口</strong>: windowTo 不能超过当前时间
 *   <li><strong>窗口大小限制</strong>: 单次窗口不超过配置的最大值
 *   <li><strong>窗口对齐</strong>: 支持按小时/天对齐窗口边界
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class PlanningWindowResolverImpl implements PlanningWindowResolver {
 *     private final CursorRepository cursorRepository;
 *     private final PlanningWindowProperties properties;
 *
 *     @Override
 *     public PlannerWindow resolve(ProvenanceCode provenanceCode, OperationCode operationCode) {
 *         // 1. 查询游标水位线
 *         var cursor = cursorRepository.findLatest(provenanceCode, operationCode);
 *
 *         // 2. 确定起始点
 *         Instant windowFrom;
 *         if (cursor.isPresent()) {
 *             // 增量采集：从游标位置开始
 *             windowFrom = cursor.get().getHighWatermark();
 *         } else {
 *             // 全量采集：从配置的回溯时间开始
 *             windowFrom = Instant.now().minus(properties.getFullHarvestLookback());
 *         }
 *
 *         // 3. 确定结束点
 *         var windowTo = windowFrom.plus(properties.getIncrementalWindowSize());
 *         var now = Instant.now();
 *         if (windowTo.isAfter(now)) {
 *             windowTo = now;  // 不能超过当前时间
 *         }
 *
 *         // 4. 构建窗口
 *         return new PlannerWindow(windowFrom, windowTo);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.plan.window;
