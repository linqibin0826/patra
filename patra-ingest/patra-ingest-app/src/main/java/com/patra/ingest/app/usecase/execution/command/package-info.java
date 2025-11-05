/**
 * Task 执行命令包。
 *
 * <p>本包定义 Task 执行用例的输入命令对象。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>封装任务执行的输入参数
 *   <li>提供幂等键用于去重
 *   <li>隔离 Adapter 层和应用层的耦合
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@code TaskReadyCommand} - 任务就绪命令
 *       <ul>
 *         <li>{@code taskId}: 任务 ID
 *         <li>{@code provenanceCode}: 数据源代码
 *         <li>{@code operationCode}: 操作代码
 *         <li>{@code idempotentKey}: 幂等键（用于防止重复消费）
 *       </ul>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * var command = TaskReadyCommand.builder()
 *     .taskId(123456L)
 *     .provenanceCode("pubmed")
 *     .operationCode(OperationCode.HARVEST)
 *     .idempotentKey("pubmed-harvest-2025-01-01:001")
 *     .build();
 *
 * taskExecutionUseCase.execute(command);
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.usecase.execution.command;
