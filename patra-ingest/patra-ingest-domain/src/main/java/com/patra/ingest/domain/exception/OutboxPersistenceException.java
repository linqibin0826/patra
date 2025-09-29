package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * Outbox 状态持久化异常。
 *
 * <p>封装 Outbox 消息状态流转（已发布 / 重试 / 死信）时对存储的 <strong>写入 / 条件更新</strong> 失败。
 * 通常成因：并发竞争（版本号/条件更新影响行数为0）、数据库临时不可用、序列获取失败。</p>
 * <p>处理建议：
 * <ul>
 *   <li>并发冲突：基于重试次数 + 指数退避再次获取并尝试。</li>
 *   <li>连接/超时：标记为可重试并交由调度再次拉起。</li>
 *   <li>持续失败（超过阈值）：告警并人工排查（库可用性 / 表锁）。</li>
 * </ul>
 * </p>
 */
public class OutboxPersistenceException extends IngestException implements HasErrorTraits {

    public enum Stage {
        /** 将消息标记为已发布（成功投递）阶段失败。 */
        MARK_PUBLISHED,
        /** 将消息标记为需重试阶段失败（可能是并发写）。 */
        MARK_RETRY,
        /** 将消息标记为死信阶段失败。 */
        MARK_DEAD
    }

    /** 失败阶段。 */
    private final Stage stage;

    /**
     * 使用阶段与消息构造。
     *
     * @param stage   失败阶段
     * @param message 描述消息
     */
    public OutboxPersistenceException(Stage stage, String message) {
        super(message);
        this.stage = stage;
    }

    /**
     * 使用阶段、消息与底层原因构造。
     *
     * @param stage   失败阶段
     * @param message 描述消息
     * @param cause   底层异常
     */
    public OutboxPersistenceException(Stage stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    /**
     * 获取失败阶段。
     *
     * @return 阶段枚举
     */
    public Stage getStage() {
        return stage;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.CONFLICT);
    }
}
