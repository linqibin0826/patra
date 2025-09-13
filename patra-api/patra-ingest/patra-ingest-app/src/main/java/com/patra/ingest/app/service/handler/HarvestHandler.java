package com.patra.ingest.app.service.handler;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.ingest.app.port.outbound.PatraRegistryPort;
import com.patra.ingest.app.usecase.command.HarvestCommand;
import com.patra.ingest.app.view.JobView;
import com.patra.ingest.domain.port.JobRepository;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.starter.expr.compiler.ExprCompiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class HarvestHandler implements IngestHandler<HarvestCommand> {

    //    private final VaultPort vaultPort;              // patra-vault：选择密钥（只拿 keyId）
//    private final Planner planner;                  // 规划器
    private final PatraRegistryPort registryPort;        // patra-registry：配置快照
    private final ExprCompiler exprCompiler;
    private final JobRepository jobRepo;            // 占位
    private final PlanRepository planRepo;          // 占位

    @Override
    public JobView handle(HarvestCommand cmd) {
        Expr expr = cmd.expr();
        // 1) 根据 provenance & 采集类型（已是 Harvest）确定处理器（本类）
        ProvenanceCode prov = cmd.provenance();

        // 2) 标准化 Expr、排序信息（系统统一 → 由 patra-query 标准化；这里只占位）
        ExprCompiler.CompileResult compileResult = exprCompiler.compile(
                expr,
                prov,
                "search",
                ExprCompiler.CompileOptions.DEFAULT
        );
        log.info("Normalized expr: {}", compileResult.query());


        // 4) 读取配置快照（baseApi、maxBatch、默认 dateType、SliceSpan 等）
        var snapshot = registryPort.getProvenanceConfigSnapshot(prov);
        log.info("Fetched provenance config snapshot: {}", snapshot);
//
//        // 5) 选择一个密钥（只返回 keyId；真正的 Secret 在发起请求时才去 vault 解密）
//        var credential = vaultPort.choose(prov, "HARVEST");
//
//        // 6) 规划 Plan（窗口优先取 Expr；否则 lastHarvestAt / cfg）
//        var lastHarvestAt = jobRepo.findLastHarvestAt(prov);     // 占位：可空
//        Plan plan = planner.makePlan(cmd.expr(), prov, snapshot, lastHarvestAt);
//
//        // 7) 生成 PlanSlice/PlanItem，并重写完整 Expr（已在 planner 中完成）
//        // 8) 为每个 PlanItem 渲染新 expr（占位调用）
//        for (var slice : plan.slices()) {
//            for (var item : slice.items()) {
//                var rendered = queryPort.render(new QueryPort.RenderRequest(
//                        prov,
//                        normalized.normalizedExpr(),  // 也可直接传 item.rewrittenExpr() 再次 normalize
//                        item.rewrittenExpr()          // 让渲染端考虑平台覆盖项（窗口、排序等）
//                ));
//                // 占位：落库 Plan/PlanSlice/Job 等
//                planRepo.saveSliceWithExpr(plan, slice, item, rendered.platformExpr(), snapshot, credential.keyId());
//            }
//        }
//
//        // 占位：创建并返回一个 Job
//        var jobId = jobRepo.createPlanJob(plan, prov, "HARVEST");
        return new JobView(1L, "PLANNED");
    }
}
