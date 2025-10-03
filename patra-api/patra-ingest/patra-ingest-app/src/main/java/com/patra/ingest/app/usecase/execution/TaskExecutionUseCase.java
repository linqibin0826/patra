package com.patra.ingest.app.usecase.execution;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;

/**
 * 任务执行用例接口。
 * <p>
 * 职责：承载任务消费与执行的应用编排逻辑，包括：
 * <ul>
 *   <li>从 MQ 消息启动任务执行（租约抢占 + 会话初始化）</li>
 *   <li>协调任务与运行记录的状态转换</li>
 *   <li>安排心跳续租以保持租约活性</li>
 * </ul>
 * </p>
 * <p>
 * 设计约束：
 * <ul>
 *   <li>应用层不引入框架依赖，仅依赖领域接口与 patra-common</li>
 *   <li>复杂 SQL（CAS/续租）由 infra 层的 Mapper XML 实现</li>
 *   <li>幂等性保障：已成功任务直接跳过，租约竞争失败时优雅退出</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskExecutionUseCase {

    /**
     * 从消息队列消费 INGEST_TASK_READY 消息后启动任务执行。
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>步骤 0：CAS 抢占租约（≤1s）</li>
     *   <li>步骤 1：初始化执行会话（置 RUNNING + 创建 TaskRun + 安排心跳）</li>
     * </ol>
     * </p>
     * <p>
     * 幂等保障：
     * <ul>
     *   <li>SUCCEEDED 且 idempotentKey 匹配 → 直接跳过</li>
     *   <li>租约抢占失败（已被他人持有）→ 优雅退出，不抛异常</li>
     *   <li>数据库/解析异常 → 抛出异常，由 MQ binder 本地重试</li>
     * </ul>
     * </p>
     *
     * @param command 任务就绪命令（包含 taskId、幂等键、调度上下文等）
     * @throws IllegalArgumentException 当命令参数校验失败时
     * @throws RuntimeException 当数据库操作失败或状态不一致时（触发 MQ 重试）
     */
    void startFromReady(TaskReadyCommand command);
}
