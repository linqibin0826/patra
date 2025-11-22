/// Task 执行命令包。
///
/// 本包定义 Task 执行用例的输入命令对象。
///
/// ## 职责
///
/// - 封装任务执行的输入参数
///   - 提供幂等键用于去重
///   - 隔离 Adapter 层和应用层的耦合
///
/// ## 核心组件
///
/// - `TaskReadyCommand` - 任务就绪命令
///
/// - `taskId`: 任务 ID
///         - `provenanceCode`: 数据源代码
///         - `operationCode`: 操作代码
///         - `idempotentKey`: 幂等键（用于防止重复消费）
///
/// ## 使用示例
///
/// ```java
/// var command = TaskReadyCommand.builder()
///     .taskId(123456L)
///     .provenanceCode("pubmed")
///     .operationCode(OperationCode.HARVEST)
///     .idempotentKey("pubmed-harvest-2025-01-01:001")
///     .build();
///
/// taskExecutionUseCase.execute(command);
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.ingest.app.usecase.execution.command;
