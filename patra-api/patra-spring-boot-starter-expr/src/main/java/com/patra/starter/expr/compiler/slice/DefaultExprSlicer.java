package com.patra.starter.expr.compiler.slice;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Atom;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.starter.expr.compiler.ExprCompiler.Issue;
import com.patra.starter.expr.compiler.ExprCompiler.Severity;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 雏形说明：
 * - 仅当 expr 是顶层 Atom，且 op=RANGE 且值为 DateRange，且 field==primaryDateField 才切片；
 * - 切片粒度：仅支持 targetWindowSize=P1M（按月）；
 * - 重写：直接用 Exprs.rangeDate(field, s, e) 生成“等价小范围”的 Atom；
 * - boundStyle/datetype：保留传入的 boundStyle；datetype 来自 FieldDictEntry.datetype。
 */
public class DefaultExprSlicer implements ExprSlicer {

    @Override
    public Outcome sliceTopLevelDateRange(Expr expr,
                                          ProvenanceSnapshot snapshot,
                                          ProvenanceCode provenance,
                                          String operation,
                                          String primaryDateField,
                                          String boundStyle,
                                          String targetWindowSize,
                                          String overlap,
                                          int maxWindowCount,
                                          boolean respectGranularity,
                                          boolean strict) {
        List<Issue> warns = new ArrayList<>();

        // 1) 主字段解析/推断
        String field = primaryDateField;
        if (field == null || field.isBlank()) {
            field = snapshot.fieldDict().values().stream()
                    .filter(ProvenanceSnapshot.FieldDictEntry::isDateField)
                    .map(ProvenanceSnapshot.FieldDictEntry::fieldKey)
                    .findFirst().orElse(null);
            if (field == null) {
                if (strict) {
                    throw new IllegalArgumentException("未找到主日期字段，且 strict=true");
                }
                warns.add(warn("W-SLICE-NO-DATE-FIELD", "未找到主日期字段，放弃切片", Map.of()));
                return new Outcome(List.of(), List.of(expr), warns);
            }
        }

        // 2) 顶层必须是 DateRange Atom（雏形限制）
        if (!(expr instanceof Atom a)
                || a.op() != Atom.Op.RANGE
                || !(a.val() instanceof Atom.DateRange dr)
                || !field.equals(a.field())) {
            warns.add(warn("W-SLICE-TOP-ONLY", "仅支持顶层 DateRange Atom 切片，已放弃", Map.of("field", field)));
            return new Outcome(List.of(), List.of(expr), warns);
        }

        // 3) 时间窗与粒度：只支持 P1M（月）
        String tw = (targetWindowSize == null ? "P1M" : targetWindowSize.toUpperCase(Locale.ROOT));
        if (!"P1M".equals(tw)) {
            warns.add(warn("W-SLICE-GRANULARITY", "当前仅支持按月切片(P1M)，已回退为 P1M", Map.of("want", tw)));
            tw = "P1M";
        }

        LocalDate from = dr.from();
        LocalDate to = dr.to();
        if (from == null || to == null) {
            if (strict) {
                throw new IllegalArgumentException("DateRange 端点不可为空（雏形限制）");
            }
            warns.add(warn("W-SLICE-OPEN-RANGE", "端点为空，无法切片，已放弃", Map.of("from", from, "to", to)));
            return new Outcome(List.of(), List.of(expr), warns);
        }
        if (to.isBefore(from)) {
            if (strict) throw new IllegalArgumentException("非法区间：to < from");
            warns.add(warn("W-SLICE-REVERSED", "to < from，放弃切片", Map.of("from", from, "to", to)));
            return new Outcome(List.of(), List.of(expr), warns);
        }

        // 4) 生成逐月窗口
        List<TimeWindow> windows = new ArrayList<>();
        List<Expr> rewritten = new ArrayList<>();

        LocalDate cursorStart = from.withDayOfMonth(1);
        LocalDate endInclusive = to;
        int count = 0;

        // datetype：来自字段字典
        String datetype = snapshot.fieldDict().get(field) != null
                ? snapshot.fieldDict().get(field).datetype()
                : null;

        while (!cursorStart.isAfter(endInclusive)) {
            LocalDate cursorEnd = cursorStart.withDayOfMonth(cursorStart.lengthOfMonth());
            if (cursorEnd.isAfter(endInclusive)) cursorEnd = endInclusive;

            windows.add(new TimeWindow(cursorStart, cursorEnd, boundStyleOrDefault(boundStyle), datetype));
            // 重写：把原来的 DateRange 替换为月窗口（顶层是 Atom，直接替换）
            rewritten.add(Exprs.rangeDate(field, cursorStart, cursorEnd));

            count++;
            if (maxWindowCount > 0 && count >= maxWindowCount) {
                warns.add(warn("W-SLICE-LIMIT", "达到最大切片数限制，后续窗口被截断", Map.of("max", maxWindowCount)));
                break;
            }
            cursorStart = cursorStart.plusMonths(1).withDayOfMonth(1);
        }

        return new Outcome(windows, rewritten, warns);
    }

    private Issue warn(String code, String msg, Map<String, Object> ctx) {
        return new Issue(Severity.WARN, code, msg, ctx);
    }

    private String boundStyleOrDefault(String s) {
        return (s == null || s.isBlank()) ? "[,]" : s;
    }
}
