package com.patra.ingest.app.usecase.plan.assembler;

import com.patra.ingest.domain.model.aggregate.PlanAssembly;

/**
 * 计划装配服务接口。
 * <p>
 * 输入 {@link PlanAssemblyRequest}（已完成窗口解析、表达式编译、配置快照收集）；输出 {@link com.patra.ingest.domain.model.aggregate.PlanAssembly}
 * 聚合集合（Plan + PlanSlice[] + Task[]），并标注 READY / FAILED 状态：
 * <ul>
 *   <li>READY：至少 1 个切片且至少 1 个任务</li>
 *   <li>FAILED：切片为空或任务为空（标记 plan.markFailed()）</li>
 * </ul>
 * </p>
 * <h4>幂等性</h4>
 * <p>由上游对 PlanKey（provenance + op + endpoint + window）控制；接口本身不保证重复调用一致返回。</p>
 * <h4>错误处理</h4>
 * <p>实现可抛出运行时异常（配置非法 / 策略未注册）；调用方应捕获并转换为领域异常。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanAssembler {

    /**
     * 执行装配流程：生成 Plan、派生切片并创建任务。
     *
     * @param request 装配请求（非 null）
     * @return 聚合装配结果（含状态）
     */
    PlanAssembly assemble(PlanAssemblyRequest request);
}
