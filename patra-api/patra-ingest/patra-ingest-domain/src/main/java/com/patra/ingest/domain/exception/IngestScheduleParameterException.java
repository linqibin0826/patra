package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * 调度任务参数异常。
 *
 * <p>用于表示 XXL 调度参数缺失、格式非法等情况，归类为业务规则违例。</p>
 */
public class IngestScheduleParameterException extends IngestException implements HasErrorTraits {

    public IngestScheduleParameterException(String message) {
        super(message);
    }

    public IngestScheduleParameterException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.RULE_VIOLATION);
    }
}
