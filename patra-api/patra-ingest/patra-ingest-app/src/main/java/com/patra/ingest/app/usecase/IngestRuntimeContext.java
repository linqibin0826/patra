package com.patra.ingest.app.usecase;

import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.enums.SchedulerSource;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static com.patra.ingest.domain.model.enums.SchedulerSource.XXL;

@Value
@Builder
public class IngestRuntimeContext {
    // —— 本次调度窗口（左开右闭） ——
    Instant windowFromExclusive;
    Instant windowToInclusive;
    ZoneId provenanceZone;        // 来源时区（用于对齐天粒度）

    // —— Registry 配置快照关键字段（避免 app 再查） ——
    ProvenanceConfigSnapshot provenanceConfig;

    // —— 审计用元信息 ——
    SchedulerSource scheduler = XXL;
    String schedulerJobId;
}
