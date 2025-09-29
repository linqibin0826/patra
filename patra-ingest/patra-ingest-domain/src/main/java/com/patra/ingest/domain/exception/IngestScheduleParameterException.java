package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * 调度任务参数异常。
 *
 * <p>场景：XXL-Job / 内部调度触发执行时，传入的 handler 参数（JSON / KV）缺失必填字段、格式不合法、数值越界或与当前操作类型不匹配。
 * 出现即表明调用方输入错误，可直接返回并记录 WARN，无需重试。</p>
 * <p>补救建议：
 * <ul>
 *   <li>检查调度中心任务配置与模板是否同步。</li>
 *   <li>对 JSON 参数进行 schema 校验前置失败（可在 adapter 层增加校验）。</li>
 *   <li>增加监控统计字段缺失次数以发现潜在模板迭代遗漏。</li>
 * </ul>
 * </p>
 */
public class IngestScheduleParameterException extends IngestException implements HasErrorTraits {

    /**
     * 仅携带描述消息构造。
     * @param message 可读描述
     */
    public IngestScheduleParameterException(String message) {
        super(message);
    }

    /**
     * 携带描述消息与底层异常构造（用于 JSON 解析/转换异常包装）。
     * @param message 描述
     * @param cause   底层异常
     */
    public IngestScheduleParameterException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.RULE_VIOLATION);
    }
}
