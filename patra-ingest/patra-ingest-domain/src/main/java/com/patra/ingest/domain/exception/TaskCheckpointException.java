package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * 任务检查点序列化/反序列化异常。
 */
public class TaskCheckpointException extends IngestException implements HasErrorTraits {

    public enum Type {
        PARSE,
        SERIALIZE
    }

    private final Type type;

    public TaskCheckpointException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.RULE_VIOLATION);
    }
}
