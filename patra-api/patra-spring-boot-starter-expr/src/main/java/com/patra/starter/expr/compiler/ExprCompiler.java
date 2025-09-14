package com.patra.starter.expr.compiler;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 编译门面（应用唯一入口）：
 * - validate：仅做结构与能力校验（不渲染）。
 * - compile：验证 + 规范化 + 渲染（产出 query 与 params）。
 * - sliceAndRewrite：基于时间窗将大 Expr 重写为多个小 Expr（不执行渲染）。
 * <p>
 * 说明：
 * 1) 仅针对“规则来源=Feign/Registry”的当前阶段；规则快照由 RuleSnapshotLoader 提供。
 * 2) 渲染结果（query/params）为“平台供应商侧”语义；参数名按快照映射（stdKey→providerParam）。
 * 3) Slicing 只重写 AST（保持语义等价）；Plan/调度由应用层负责。
 */
public interface ExprCompiler {

    Boolean hasAtom(Expr expr);


    // ========= 编译（验证 + 渲染） =========

    /**
     * 完整编译：normalize + validate + normalize + render。
     */
    CompileResult compile(Expr expr,
                          ProvenanceCode provenance,
                          String operation,
                          CompileOptions options);

    // ========= 切片重写 =========

    /**
     * 将包含“大时间范围”的 Expr 切片为多个小时间窗的等价 Expr 列表（仅重写 AST）。
     * - 选择的日期字段由 options.primaryDateField 决定（或由能力/字典自动推断）。
     * - 边界/粒度遵循供应商能力（必要时上调到月/季粒度，并给出 warning）。
     */
    SliceResult sliceAndRewrite(Expr expr,
                                ProvenanceCode provenance,
                                String operation,
                                SliceOptions options);

    // ================== DTO / Options / Report ==================

    // ---- 校验 ----
    record ValidateOptions(boolean strict) {
        public static ValidateOptions DEFAULT = new ValidateOptions(true);
    }

    record ValidationReport(List<Issue> warnings, List<Issue> errors) {
        public boolean ok() {
            return errors == null || errors.isEmpty();
        }
    }

    record Issue(Severity severity, String code, String message, Map<String, Object> ctx) {
    }

    enum Severity {INFO, WARN, ERROR}

    // ---- 编译 ----
    record CompileOptions(
            boolean strict,
            int maxQueryLength,              // 整体 query 上限（0=不限制）
            String timezone,                 // 例如 "UTC" 或 "Asia/Shanghai"
            boolean traceEnabled             // 是否输出渲染轨迹（便于观测）
    ) {
        public static CompileOptions DEFAULT = new CompileOptions(true, 0, "UTC", false);
    }

    record CompileResult(
            String query,                    // 渲染后的 query 片段（emit=query 的聚合）
            Map<String, String> params,      // 渲染后的供应商参数（emit=params）
            ValidationReport report,         // 包含校验警告/错误（compile 内部产生）
            SnapshotRef snapshot,            // 使用的规则版本（便于幂等/审计）
            RenderTrace trace                // 可选：命中规则与模板轨迹（traceEnabled=true）
    ) {
    }

    record SnapshotRef(
            Long provenanceId,
            String provenanceCode,
            String operation,
            long version,
            Instant updatedAt
    ) {
    }

    // 渲染轨迹：只给必要字段，后续可扩展
    record RenderTrace(
            List<Hit> hits                   // 每个 Atom 命中的规则及模板摘要
    ) {
        public record Hit(String fieldKey, String op, Integer priority, String templateId) {
        }
    }

    // ---- 切片 ----
    record SliceOptions(
            String primaryDateField,         // 主日期字段，如 "dp"；为空则自动推断
            String alignTo,                  // CALENDAR | ROLLING
            String targetWindowSize,         // ISO-8601 周期：P7D/P1M/P1Q/P1Y
            String overlap,                  // ISO-8601 周期：P0D/P1D...
            int maxWindowCount,              // 最大切片数，防止爆炸（<=0 表示不限制）
            String boundStyle,               // "[,]" | "[,)" | "(,]" | "(,)"
            boolean respectGranularity,      // 遵循供应商粒度（不足时上调）
            boolean strict                   // 严格模式：无法确定主时间窗则报错
    ) {
        public static SliceOptions DEFAULT = new SliceOptions(
                null, "CALENDAR", "P1M", "P0D", 200, "[,]", true, true
        );
    }

    record SliceResult(
            List<TimeWindow> windows,        // 切片后的时间窗
            List<Expr> rewrittenExprs,       // 与 windows 一一对应
            List<Issue> warnings,            // 上调粒度/对齐边界/截断等
            SnapshotRef snapshot             // 参与决策的规则版本
    ) {
    }

    record TimeWindow(
            LocalDate startDate,             // 日粒度；若为 datetime 源，业务可再外层转成 instant
            LocalDate endDate,
            String boundStyle,               // 同上：[,], [,), (,], (,)
            String datetype                  // PDAT/EDAT/MHDA（若适用，否则 null）
    ) {
    }
}
