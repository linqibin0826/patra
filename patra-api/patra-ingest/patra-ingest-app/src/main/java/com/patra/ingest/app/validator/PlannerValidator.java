package com.patra.ingest.app.validator;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.domain.exception.PlanValidationException;

/**
 * 计划装配前置验证器契约。
 * <p>负责在构建 Plan / Slice / Task 之前进行输入与环境健康检查，防止后续产生大量无效任务。</p>
 * <p>典型校验纬度（实现可按需扩展）：
 * <ul>
 *   <li>窗口合法性（存在性、时间顺序、跨度上下限）</li>
 *   <li>队列背压（当前排队任务阈值）</li>
 *   <li>来源能力匹配（增量/全量、偏移字段配置完整性）</li>
 *   <li>配置快照完整性（必要字段是否缺失）</li>
 * </ul>
 * 失败策略：违反约束抛出 {@link PlanValidationException}，调用方应捕获并记录业务级告警而非系统异常。
 */
public interface PlannerValidator {

    /**
     * 在计划装配前执行验证。
     * @param triggerNorm 触发归一化对象（含来源/操作/请求窗口）
     * @param snapshot 来源配置快照（可能为空）
     * @param window 归一化后的计划窗口（可能为空：UPDATE 等操作）
     * @param currentQueuedTasks 当前排队任务数，用于背压判断
     * @throws PlanValidationException 校验失败时抛出
     */
    void validateBeforeAssemble(PlanTriggerNorm triggerNorm,
                                ProvenanceConfigSnapshot snapshot,
                                PlannerWindow window,
                                long currentQueuedTasks);
}

