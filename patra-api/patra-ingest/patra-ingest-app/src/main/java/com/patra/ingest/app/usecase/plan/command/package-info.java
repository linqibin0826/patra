/// Plan 摄入命令包。
///
/// 本包定义 Plan 摄入用例的输入命令对象。
///
/// ## 职责
///
/// - 封装 Plan 摄入用例的输入参数
///   - 提供输入验证（使用 JSR-303 注解）
///   - 隔离 Adapter 层和应用层的耦合
///
/// ## 核心组件
///
/// - `PlanIngestionCommand` - Plan 摄入命令
///
/// - `provenanceCode`: 数据源代码（如 pubmed、epmc）
///         - `operationCode`: 操作代码（如 HARVEST、UPDATE）
///         - `triggerType`: 触发类型（SCHEDULED、MANUAL、RETRY）
///         - `windowFrom`: 窗口起始时间
///         - `windowTo`: 窗口结束时间
///         - `sliceStrategyCode`: 切片策略（TIME、DATE、SINGLE）
///
/// ## 使用示例
///
/// ```java
/// var command = PlanIngestionCommand.builder()
///     .provenanceCode("pubmed")
///     .operationCode(OperationCode.HARVEST)
///     .triggerType(TriggerType.SCHEDULED)
///     .windowFrom(Instant.parse("2025-01-01T00:00:00Z"))
///     .windowTo(Instant.parse("2025-01-10T00:00:00Z"))
///     .sliceStrategyCode("TIME")
///     .build();
///
/// var result = planIngestionUseCase.ingestPlan(command);
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.plan.command;
