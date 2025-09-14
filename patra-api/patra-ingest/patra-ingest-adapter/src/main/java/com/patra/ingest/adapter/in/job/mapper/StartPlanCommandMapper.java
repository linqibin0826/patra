package com.patra.ingest.adapter.in.job.mapper;

import com.patra.ingest.adapter.in.job.Window;
import com.patra.ingest.app.usecase.command.StartPlanCommand;

public final class StartPlanCommandMapper {
    private StartPlanCommandMapper(){}

    public static StartPlanCommand mapForDailyHarvest(
            String exprProtoJson,
            Window window,
            String schedulerJobId
    ){
        return StartPlanCommand.builder()
                .exprProtoJson(exprProtoJson) // 允许为 null/blank
                .windowFromExclusive(window.fromExclusive())
                .windowToInclusive(window.toInclusive())
                .boundStyle(StartPlanCommand.BoundStyle.OPEN_LEFT_CLOSED_RIGHT)
                .sliceStrategy(StartPlanCommand.SliceStrategy.TIME)
                .triggerSource("xxl")
                .schedulerJobId(schedulerJobId)
                .build();
    }
}
