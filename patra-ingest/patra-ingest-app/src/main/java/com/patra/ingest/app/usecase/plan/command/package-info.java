/**
 * Plan 摄入命令包。
 *
 * <p>本包定义 Plan 摄入用例的输入命令对象。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>封装 Plan 摄入用例的输入参数
 *   <li>提供输入验证（使用 JSR-303 注解）
 *   <li>隔离 Adapter 层和应用层的耦合
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code PlanIngestionCommand} - Plan 摄入命令
 *       <ul>
 *         <li>{@code provenanceCode}: 数据源代码（如 pubmed、epmc）
 *         <li>{@code operationCode}: 操作代码（如 HARVEST、UPDATE）
 *         <li>{@code triggerType}: 触发类型（SCHEDULED、MANUAL、RETRY）
 *         <li>{@code windowFrom}: 窗口起始时间
 *         <li>{@code windowTo}: 窗口结束时间
 *         <li>{@code sliceStrategyCode}: 切片策略（TIME、DATE、SINGLE）
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * var command = PlanIngestionCommand.builder()
 *     .provenanceCode("pubmed")
 *     .operationCode(OperationCode.HARVEST)
 *     .triggerType(TriggerType.SCHEDULED)
 *     .windowFrom(Instant.parse("2025-01-01T00:00:00Z"))
 *     .windowTo(Instant.parse("2025-01-10T00:00:00Z"))
 *     .sliceStrategyCode("TIME")
 *     .build();
 *
 * var result = planIngestionUseCase.ingestPlan(command);
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.plan.command;
