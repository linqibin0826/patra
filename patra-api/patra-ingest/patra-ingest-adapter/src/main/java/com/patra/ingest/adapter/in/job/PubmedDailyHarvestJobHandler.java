package com.patra.ingest.adapter.in.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.adapter.in.job.mapper.StartPlanCommandMapper;
import com.patra.ingest.adapter.out.registry.mapper.RegistryAclConverter;
import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;
import com.patra.ingest.app.usecase.IngestRuntimeContext;
import com.patra.ingest.app.usecase.StartPlanUseCase;
import com.patra.ingest.app.usecase.command.StartPlanCommand;
import com.patra.ingest.domain.model.enums.OperationType;
import com.patra.registry.api.rpc.client.LiteratureProvenanceClient;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PubmedDailyHarvestJobHandler {

    private final StartPlanUseCase startPlanUseCase;           // app 层用例端口
    private final LiteratureProvenanceClient registryClient;  // Registry Feign
    private final Clock clock = Clock.systemUTC();            // 可改为 @Bean 注入，方便单测
    private final ObjectMapper objectMapper;
    private final RegistryAclConverter registryAclConverter;

    @XxlJob("pubmedDailyHarvestJob")
    public ReturnT<String> handle() {
        try {
            // 1) 解析 XXL 入参（可空）
            var raw = XxlJobHelper.getJobParam();
            var payload = (raw == null || raw.isBlank())
                    ? new XxlJobPayload()
                    : objectMapper.readValue(raw, XxlJobPayload.class);

            // 2) 来源与操作（强制 HARVEST）
            var provenance = Optional.ofNullable(payload.getProvenanceCode())
                    .filter(s -> !s.isBlank())
                    .map(String::toUpperCase)
                    .map(ProvenanceCode::valueOf)
                    .orElse(ProvenanceCode.PUBMED);
            var operation = OperationType.HARVEST;

            // 3) 读取 Registry 配置并转换为中立快照（含 overlapDays）
            var cfgResp = registryClient.getConfigByCode(provenance);
            ProvenanceConfigSnapshot cfg = registryAclConverter.toProvenanceConfigSnapshot(cfgResp);
            ZoneId zone = cfg.timezone(); // 来源时区

            // 4) 计算前一日窗口：(D-1 00:00, D 00:00] @sourceTZ
            var window = TimeWindowCalculator.previousFullDayOpenLeftClosedRight(zone, clock);

            // 5) 组装应用层命令与运行上下文（把 overlapDays 等都带过去）
            StartPlanCommand cmd = StartPlanCommandMapper.mapForDailyHarvest(
                    payload.getExprProtoJson(),
                    window,
                    String.valueOf(XxlJobHelper.getJobId())
            );

            IngestRuntimeContext ctx = IngestRuntimeContext.builder()
                    .windowFromExclusive(window.fromExclusive())
                    .windowToInclusive(window.toInclusive())
                    .provenanceZone(zone)
                    .provenanceConfig(cfg)                 // <—— 关键：携带 overlapDays 等
                    .schedulerJobId(String.valueOf(XxlJobHelper.getJobId()))
                    .build();

            // 6) 调用 app 用例（adapter 不做采集/分页）
            Long planId = startPlanUseCase.startPlan(cmd, ctx);

            // 7) 记录日志与返回
            log.info("[pubmedDailyHarvestJob] submitted: planId={}, prov={}, op={}, window=({},{}], tz={}, overlapDays={}",
                    planId, provenance, operation,
                    window.fromExclusive(), window.toInclusive(),
                    zone, cfg.windowPolicy().overlapDays());
            return ReturnT.SUCCESS;

        } catch (Throwable t) {
            log.error("[pubmedDailyHarvestJob] failed", t);
            XxlJobHelper.log("Exception: " + t.getMessage());
            return ReturnT.FAIL;
        }
    }
}
