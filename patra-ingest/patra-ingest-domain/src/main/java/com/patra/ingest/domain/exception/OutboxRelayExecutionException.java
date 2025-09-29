package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * Outbox Relay 执行过程中产生的异常。
 *
 * <p>表示在“拉取待发布消息 → 尝试租约/加锁 → 发布到消息中枢 → 标记状态”流程任一步骤发生非持久化类故障（网络中断、第三方 SDK 调用失败、序列化失败等）。
 * 与 {@link OutboxPersistenceException} 区别：本异常更偏向外部依赖 / 发布链路，而非状态落库。</p>
 * <p>处理策略：
 * <ul>
 *   <li>可恢复（网络抖动）：标记重试。</li>
 *   <li>不可恢复（格式不支持 / 目标拒绝）：根据策略进入死信。</li>
 * </ul>
 * </p>
 */
public class OutboxRelayExecutionException extends IngestException implements HasErrorTraits {

    /**
     * 使用消息与底层异常构造。
     *
     * @param message 描述消息
     * @param cause   底层异常
     */
    public OutboxRelayExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.DEP_UNAVAILABLE);
    }
}
