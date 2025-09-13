package com.patra.starter.expr.compiler.slice;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.ExprCompiler.Issue;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.time.LocalDate;
import java.util.List;

/**
 * 表达式切片器（雏形）：
 * 仅支持：expr 为“顶层 Atom 且为主日期字段的 DATE RANGE”时，按月切片。
 * 其余复杂布尔结构留到下一步迭代。
 */
public interface ExprSlicer {

    Outcome sliceTopLevelDateRange(Expr expr,
                                   ProvenanceSnapshot snapshot,
                                   ProvenanceCode provenance,
                                   String operation,
                                   String primaryDateField,
                                   String boundStyle,          // "[,]" / "[,)" / "(,]" / "(,)"
                                   String targetWindowSize,    // 先只支持 P1M（月）
                                   String overlap,             // 先忽略重叠，保留参数位
                                   int maxWindowCount,
                                   boolean respectGranularity, // 暂不生效，占位
                                   boolean strict              // 找不到日期原子时是否报错
    );

    // 结果：窗口 + 重写后的 expr（与窗口一一对应）+ 警告
    record Outcome(List<TimeWindow> windows, List<Expr> rewritten, List<Issue> warnings) {
    }

    record TimeWindow(LocalDate startDate, LocalDate endDate, String boundStyle, String datetype) {
    }
}
