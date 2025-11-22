package com.patra.ingest.app.usecase.plan;

import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;

/// 计划接入用例接口，定义调度入口契约。
///
/// @author linqibin
/// @since 0.1.0
public interface PlanIngestionUseCase {

  /// 编排并持久化 Plan/Slice/Task，触发 Outbox 发布。
  ///
  /// @param request 调度请求
  /// @return 编排结果摘要
  PlanIngestionResult ingestPlan(PlanIngestionCommand request);
}
