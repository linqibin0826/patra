package com.patra.ingest.app.engine;

import com.patra.ingest.domain.model.aggregate.PlanAssembly;
import com.patra.ingest.domain.model.command.PlanBlueprintCommand;

/**
 * 计划编排引擎（应用层组件）。
 */
public interface PlannerEngine {

    PlanAssembly assemble(PlanBlueprintCommand command);
}

