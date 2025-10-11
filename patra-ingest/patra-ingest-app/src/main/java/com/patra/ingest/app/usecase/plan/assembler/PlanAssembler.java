package com.patra.ingest.app.usecase.plan.assembler;

import com.patra.ingest.app.usecase.plan.dto.PlanAssemblyResult;

/**
 * Plan assembly service contract.
 *
 * <p>Accepts a fully prepared {@link PlanAssemblyRequest} (window resolved, expression
 * compiled, configuration snapshot collected) and returns a {@link PlanAssemblyResult}
 * containing Plan + PlanSlice[] + Task[] aggregates alongside a READY / FAILED status:</p>
 * <ul>
 *   <li>READY: at least one slice and one task were produced.</li>
 *   <li>FAILED: slices or tasks are missing; the plan is marked failed.</li>
 * </ul>
 *
 * <h4>Idempotency</h4>
 * <p>Enforced upstream via the plan key (provenance + operation + endpoint + window). This
 * interface does not guarantee idempotent responses on repeated invocation.</p>
 *
 * <h4>Error handling</h4>
 * <p>Implementations may throw runtime exceptions for invalid configuration or missing
 * policies; callers should translate them into domain exceptions.</p>
 */
public interface PlanAssembler {

    /**
     * 执行装配流程：生成 Plan、派生切片并创建任务。
     *
     * @param request 装配请求（非 null）
     * @return 聚合装配结果（含状态）
     */
    PlanAssemblyResult assemble(PlanAssemblyRequest request);
}
