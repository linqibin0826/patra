package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.EnumSet;
import java.util.Set;

/**
 * 计划相关持久化异常。
 *
 * <p>封装计划编排落地过程中各结构（调度实例、计划、切片、任务、任务重试记录）写入/更新/查询的失败。
 * 绝大多数由数据库/中间件不可用、网络抖动、乐观锁冲突或序列生成问题导致。可区分是否适合重试：</p>
 * <ul>
 *   <li>瞬时连接 / 超时类：应用层可进行有限次数重试。</li>
 *   <li>乐观锁冲突：根据 {@link #getStage()} 定位是哪一层需重建或回读刷新。</li>
 *   <li>数据约束违反：应记录并告警，避免盲目重试放大问题。</li>
 * </ul>
 */
public class PlanPersistenceException extends IngestException implements HasErrorTraits {

    /**
     * 持久化阶段分类。
     * <p>用于精准定位失败位置，支持差异化重试/补偿。</p>
     */
    public enum Stage {
        /** 调度实例（如 schedule run 记录）持久化失败。 */
        SCHEDULE_INSTANCE,
        /** 计划实体（Plan 主记录）写入/更新失败。 */
        PLAN,
        /** 计划切片（Plan Slice）落地失败。 */
        PLAN_SLICE,
        /** 任务（Task）写入/更新失败。 */
        TASK,
        /** 任务重试（Task Retry / Attempt）记录写入失败。 */
        TASK_RETRY
    }

    /** 失败发生的阶段。 */
    private final Stage stage;

    /**
     * 使用阶段与消息构造。
     *
     * @param stage   失败阶段
     * @param message 描述消息
     */
    public PlanPersistenceException(Stage stage, String message) {
        this(stage, message, null);
    }

    /**
     * 使用阶段、消息与底层原因构造。
     *
     * @param stage   失败阶段
     * @param message 描述消息
     * @param cause   底层异常
     */
    public PlanPersistenceException(Stage stage, String message, Throwable cause) {
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
        return EnumSet.of(ErrorTrait.DEP_UNAVAILABLE);
    }
}
