package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * 任务检查点序列化 / 反序列化异常。
 *
 * <p>在任务运行过程中需要持久化或恢复运行进度（checkpoint）时，若 JSON / 二进制编码转换失败、字段缺失、版本不兼容，则抛出本异常。</p>
 * <p>处理策略：
 * <ul>
 *   <li>PARSE（读取失败）：回退为“从头”或终止任务并人工介入（视幂等性）。</li>
 *   <li>SERIALIZE（写出失败）：可限次重试；持续失败需告警，避免进度丢失造成重复处理。</li>
 * </ul>
 * </p>
 */
public class TaskCheckpointException extends IngestException implements HasErrorTraits {

    public enum Type {
        /** 解析已有检查点数据失败。 */
        PARSE,
        /** 序列化新的检查点数据失败。 */
        SERIALIZE
    }

    /** 异常类型。 */
    private final Type type;

    /**
     * 构造异常。
     *
     * @param type    异常类型
     * @param message 描述消息
     * @param cause   底层异常
     */
    public TaskCheckpointException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    /**
     * 获取异常类型。
     *
     * @return 类型枚举
     */
    public Type getType() {
        return type;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.RULE_VIOLATION);
    }
}
