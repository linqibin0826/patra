/**
 * 切片规划器包。
 *
 * <p>本包提供将大的 Plan 窗口分解为可管理的 Slice 的策略实现。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>根据切片策略将 Plan 窗口分解为多个 Slice
 *   <li>为每个 Slice 分配序列号（seq）
 *   <li>确保 Slice 窗口的连续性和不重叠性
 *   <li>支持多种切片策略（TIME、DATE、SINGLE）
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code SlicePlanner} - 切片规划器接口
 *   <li>{@code SlicePlannerRegistry} - 切片规划器注册表
 *   <li>{@code TimeSlicePlanner} - 时间范围切片规划器
 *       <ul>
 *         <li>按固定时间间隔切片（如每小时一个 Slice）
 *       </ul>
 *   <li>{@code DateSlicePlanner} - 日期切片规划器
 *       <ul>
 *         <li>按日期切片（如每天一个 Slice）
 *       </ul>
 *   <li>{@code SingleSlicePlanner} - 单切片规划器
 *       <ul>
 *         <li>整个 Plan 只有一个 Slice（不切片）
 *       </ul>
 * </ul>
 *
 * <h2>切片策略</h2>
 * <table border="1">
 *   <tr>
 *     <th>策略代码</th>
 *     <th>实现类</th>
 *     <th>切片粒度</th>
 *     <th>适用场景</th>
 *   </tr>
 *   <tr>
 *     <td>TIME</td>
 *     <td>TimeSlicePlanner</td>
 *     <td>按小时/分钟</td>
 *     <td>实时增量采集</td>
 *   </tr>
 *   <tr>
 *     <td>DATE</td>
 *     <td>DateSlicePlanner</td>
 *     <td>按天</td>
 *     <td>全量/增量采集</td>
 *   </tr>
 *   <tr>
 *     <td>SINGLE</td>
 *     <td>SingleSlicePlanner</td>
 *     <td>不切片</td>
 *     <td>小数据量采集</td>
 *   </tr>
 * </table>
 *
 * <h2>切片示例</h2>
 * <h3>TIME 策略（按小时切片）</h3>
 * <pre>
 * 输入窗口: 2025-01-01 00:00:00 ~ 2025-01-01 03:00:00
 *
 * 输出 Slice:
 *   Slice 1: 2025-01-01 00:00:00 ~ 2025-01-01 01:00:00 (seq=1)
 *   Slice 2: 2025-01-01 01:00:00 ~ 2025-01-01 02:00:00 (seq=2)
 *   Slice 3: 2025-01-01 02:00:00 ~ 2025-01-01 03:00:00 (seq=3)
 * </pre>
 *
 * <h3>DATE 策略（按天切片）</h3>
 * <pre>
 * 输入窗口: 2025-01-01 ~ 2025-01-05
 *
 * 输出 Slice:
 *   Slice 1: 2025-01-01 00:00:00 ~ 2025-01-02 00:00:00 (seq=1)
 *   Slice 2: 2025-01-02 00:00:00 ~ 2025-01-03 00:00:00 (seq=2)
 *   Slice 3: 2025-01-03 00:00:00 ~ 2025-01-04 00:00:00 (seq=3)
 *   Slice 4: 2025-01-04 00:00:00 ~ 2025-01-05 00:00:00 (seq=4)
 * </pre>
 *
 * <h3>SINGLE 策略（不切片）</h3>
 * <pre>
 * 输入窗口: 2025-01-01 ~ 2025-01-05
 *
 * 输出 Slice:
 *   Slice 1: 2025-01-01 00:00:00 ~ 2025-01-05 00:00:00 (seq=1)
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class TimeSlicePlanner implements SlicePlanner {
 *     private final SlicePlannerProperties properties;
 *
 *     @Override
 *     public String getStrategyCode() {
 *         return "TIME";
 *     }
 *
 *     @Override
 *     public List<SlicePlan> plan(SlicePlanningContext context) {
 *         var window = context.getPlannerWindow();
 *         var sliceInterval = properties.getTimeSliceInterval();  // 如 1 小时
 *
 *         var slices = new ArrayList<SlicePlan>();
 *         var current = window.getFrom();
 *         int seq = 1;
 *
 *         while (current.isBefore(window.getTo())) {
 *             var sliceEnd = current.plus(sliceInterval);
 *             if (sliceEnd.isAfter(window.getTo())) {
 *                 sliceEnd = window.getTo();
 *             }
 *
 *             slices.add(SlicePlan.builder()
 *                 .window(new PlannerWindow(current, sliceEnd))
 *                 .seq(seq++)
 *                 .build());
 *
 *             current = sliceEnd;
 *         }
 *
 *         return slices;
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.plan.slicer;
