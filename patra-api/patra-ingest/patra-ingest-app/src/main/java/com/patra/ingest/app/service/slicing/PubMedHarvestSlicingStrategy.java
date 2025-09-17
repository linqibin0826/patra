package com.patra.ingest.app.service.slicing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.IngestDateType;
import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.ingest.app.model.registry.ProvenanceConfigSnapshot;
import com.patra.ingest.app.model.snapshot.ExprSnapshot;
import com.patra.ingest.app.model.snapshot.SliceSpecSnapshot;
import com.patra.ingest.app.usecase.command.JobStartPlanCommand;
import com.patra.ingest.app.usecase.command.CursorSpec;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.starter.expr.compiler.ExprCompiler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * PubMed + HARVEST 的 MVP 切片策略：
 * - 仅支持时间窗口（TIME）单片；后续可改为多片（按月/日等）。
 * - dateField 默认取 Registry 配置的 {@link ProvenanceConfigSnapshot#dateFieldDefault()}，可被命令覆盖。
 */
@Component
@RequiredArgsConstructor
public class PubMedHarvestSlicingStrategy implements SlicingStrategy {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(ProvenanceCode provenance, IngestOperationType operation) {
        return provenance == ProvenanceCode.PUBMED && operation == IngestOperationType.HARVEST;
    }

    @Override
    public List<SliceDraft> slice(Expr baseExpr,
                                  ProvenanceCode provenance,
                                  IngestOperationType operation,
                                  ExprCompiler compiler,
                                  ProvenanceConfigSnapshot cfg,
                                  JobStartPlanCommand command,
                                  Long planId,
                                  ZoneId zone) {

        // 选择时间字段：命令覆盖 > 配置默认
        IngestDateType dateField = command.overrides().timeFieldName()
                .map(IngestDateType::fromCode)
                .orElse(cfg.dateFieldDefault());

        // 解析边界：命令 CursorSpec since/until（若为空则留空，交给供应商端默认）
        CursorSpec cursor = command.cursorSpec();
        Instant from = cursor.since().orElse(null);
        Instant to = cursor.until().orElse(null);

        // 构造顶层时间范围表达式（UTC Instant）。PubMed 可接受日粒度，MVP 先直接使用 DateTimeRange。
        // 构造表达式（如有需要）留给上层；此处仅依赖时间边界生成快照 JSON。

        // 生成稳定化切片规范 JSON（time 单片）
        SliceSpecSnapshot spec = SliceSpecSnapshot.builder()
                .kind("time")
                .dateField(dateField)
                .fromInclusive(from == null ? null : from.toString())
                .toExclusive(to == null ? null : to.toString())
                .timezone(zone == null ? null : zone.getId())
                .overlapDays(cfg.windowPolicy() == null ? null : cfg.windowPolicy().overlapDays())
                .build();

        String specJson = writeJson(spec);

        // 生成局部化表达式快照 JSON（用于哈希/追溯）
        ExprSnapshot exprSnap = ExprSnapshot.builder()
                .type("rangeDateTime")
                .dateField(dateField)
                .fromInclusive(from == null ? null : from.toString())
                .toExclusive(to == null ? null : to.toString())
                .includeFrom(true)
                .includeTo(false)
                .hasAtom(true)
                .baseProto(null)
                .build();

        String exprSnapJson = writeJson(exprSnap);

        // 单片：sliceNo=0
        return List.of(new SliceDraft(0, specJson, exprSnapJson));
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize snapshot", e);
        }
    }
}
