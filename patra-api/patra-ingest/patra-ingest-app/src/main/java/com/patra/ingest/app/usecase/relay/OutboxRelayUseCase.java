package com.patra.ingest.app.usecase.relay;

import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;

/**
 * Outbox Relay 用例契约（应用层对外暴露的编排入口）。
 * <p>职责：接受调度/人工/运维触发的 {@link OutboxRelayCommand}，执行一次批量 Outbox 消息发布并返回统计结果。</p>
 * <p>语义：单次调用内不做无限重试；失败消息与待重试消息由领域层状态 + 调度循环驱动后续恢复。</p>
 */
public interface OutboxRelayUseCase {

    /**
     * 执行一次 Outbox Relay。
     * <p>实现需确保：
     * <ul>
     *   <li>幂等性：相同消息不会重复发布（依赖租约 + 状态更新）</li>
     *   <li>观测性：关键统计字段写入日志</li>
     * </ul>
     * </p>
     * @param instruction 调度指令（可覆盖默认配置）
     * @return 执行结果报告
     */
    RelayReport relay(OutboxRelayCommand instruction);
}
