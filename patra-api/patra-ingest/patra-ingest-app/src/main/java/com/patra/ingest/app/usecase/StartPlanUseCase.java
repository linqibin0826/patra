package com.patra.ingest.app.usecase;

import com.patra.ingest.app.usecase.command.StartPlanCommand;

public interface StartPlanUseCase {
    /**
     * 触发一次采集计划（计划/切片/任务创建、schedule_instance 落库等均在 app 内完成）
     * @return planId
     */
    Long startPlan(StartPlanCommand command, IngestRuntimeContext context);


}
