package com.patra.ingest.app.orchestration.application;

import com.patra.ingest.app.orchestration.command.PlanIngestionRequest;
import com.patra.ingest.app.orchestration.dto.PlanIngestionResult;

/**
 * 计划编排应用层用例接口，定义调度入口契约。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanIngestionUseCase {

    /**
     * 编排并持久化计划/切片/任务，同时触发出箱。
     *
     * @param request 调度请求
     * @return 编排结果摘要
     */
    PlanIngestionResult ingestPlan(PlanIngestionRequest request);
}
