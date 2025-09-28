package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * 计划相关持久化异常。
 *
 * <p>封装计划、切片、任务或调度实例在持久化阶段的失败，通常由存储层异常导致。</p>
 */
public class PlanPersistenceException extends IngestException implements HasErrorTraits {

    /** 持久化阶段分类。 */
    public enum Stage {
        SCHEDULE_INSTANCE,
        PLAN,
        PLAN_SLICE,
        TASK,
        TASK_RETRY
    }

    private final Stage stage;

    public PlanPersistenceException(Stage stage, String message) {
        this(stage, message, null);
    }

    public PlanPersistenceException(Stage stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.DEP_UNAVAILABLE);
    }
}
