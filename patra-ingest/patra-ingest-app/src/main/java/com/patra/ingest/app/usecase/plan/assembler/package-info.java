/**
 * Plan 装配器包。
 *
 * <p>本包负责装配 Plan/Slice/Task 聚合根的完整对象图，协调切片规划器和表达式构建器。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>协调切片规划（SlicePlanner）生成 Slice 列表
 *   <li>为每个 Slice 生成对应的 Task
 *   <li>装配 Plan 聚合根（包含 Slice 和 Task）
 *   <li>设置聚合根之间的关联关系（planId、sliceId）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code PlanAssembler} - Plan 装配器接口
 *   <li>{@code PlanAssemblerImpl} - Plan 装配器实现
 *   <li>{@code PlanAssemblyRequest} - 装配请求（输入）
 * </ul>
 *
 * <h2>装配流程</h2>
 *
 * <pre>
 * 1. 输入：PlanAssemblyRequest
 *    ├─ provenanceCode
 *    ├─ operationCode
 *    ├─ plannerWindow
 *    ├─ planExpressionDescriptor
 *    └─ sliceStrategyCode
 *
 * 2. 切片规划
 *    └─ SlicePlanner.plan(window) → List<SlicePlan>
 *
 * 3. 装配 Plan
 *    └─ PlanAggregate.create(...)
 *
 * 4. 装配 Slice（for each SlicePlan）
 *    └─ PlanSliceAggregate.create(planId, window, ...)
 *
 * 5. 装配 Task（for each Slice）
 *    └─ TaskAggregate.create(planId, sliceId, ...)
 *
 * 6. 输出：PlanAssemblyResult
 *    ├─ plan: PlanAggregate
 *    ├─ slices: List<PlanSliceAggregate>
 *    └─ tasks: List<TaskAggregate>
 * </pre>
 *
 * <h2>关键设计</h2>
 *
 * <ul>
 *   <li><strong>1:1 关系</strong>: 每个 Slice 对应一个 Task（简化设计）
 *   <li><strong>临时 ID</strong>: 装配时使用临时 ID（如 -1），持久化后替换为真实 ID
 *   <li><strong>快照保存</strong>: Plan 中保存配置快照和表达式快照（避免配置变更影响执行）
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class PlanAssemblerImpl implements PlanAssembler {
 *     private final SlicePlannerRegistry slicePlannerRegistry;
 *
 *     @Override
 *     public PlanAssemblyResult assemble(PlanAssemblyRequest request) {
 *         // 1. 获取切片规划器
 *         var slicePlanner = slicePlannerRegistry.getPlanner(request.getSliceStrategyCode());
 *
 *         // 2. 规划切片
 *         var slicePlans = slicePlanner.plan(SlicePlanningContext.builder()
 *             .plannerWindow(request.getPlannerWindow())
 *             .provenanceCode(request.getProvenanceCode())
 *             .build());
 *
 *         // 3. 装配 Plan
 *         var plan = PlanAggregate.create(
 *             request.getProvenanceCode(),
 *             request.getOperationCode(),
 *             request.getPlannerWindow(),
 *             request.getExpressionDescriptor(),
 *             request.getConfigSnapshot()
 *         );
 *
 *         // 4. 装配 Slice 和 Task
 *         var slices = new ArrayList<PlanSliceAggregate>();
 *         var tasks = new ArrayList<TaskAggregate>();
 *
 *         for (var slicePlan : slicePlans) {
 *             var slice = PlanSliceAggregate.create(
 *                 plan.getId(),  // 临时 ID
 *                 slicePlan.getWindow(),
 *                 slicePlan.getSeq()
 *             );
 *             slices.add(slice);
 *
 *             var task = TaskAggregate.create(
 *                 plan.getId(),
 *                 slice.getId(),
 *                 request.getProvenanceCode(),
 *                 request.getOperationCode()
 *             );
 *             tasks.add(task);
 *         }
 *
 *         // 5. 返回装配结果
 *         return new PlanAssemblyResult(plan, slices, tasks);
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.plan.assembler;
