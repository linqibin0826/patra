package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * Outbox 状态持久化异常。
 *
 * <p>用于封装 Outbox 状态写入、重试、标记等过程中的失败，提示并发冲突或存储异常。</p>
 */
public class OutboxPersistenceException extends IngestException implements HasErrorTraits {

    public enum Stage {
        MARK_PUBLISHED,
        MARK_RETRY,
        MARK_DEAD
    }

    private final Stage stage;

    public OutboxPersistenceException(Stage stage, String message) {
        super(message);
        this.stage = stage;
    }

    public OutboxPersistenceException(Stage stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }

    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return EnumSet.of(ErrorTrait.CONFLICT);
    }
}
