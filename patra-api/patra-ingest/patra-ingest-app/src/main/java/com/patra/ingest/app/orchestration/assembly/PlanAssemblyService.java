package com.patra.ingest.app.orchestration.assembly;

import com.patra.ingest.domain.model.aggregate.PlanAssembly;

/**
 * 计划装配服务接口，负责将触发请求转化为计划/切片/任务聚合。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanAssemblyService {

    /**
     * 执行装配流程。
     *
     * @param request 装配请求
     * @return 装配产物
     */
    PlanAssembly assemble(PlanAssemblyRequest request);
}
