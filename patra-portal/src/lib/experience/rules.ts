import {
  type FinalOutcome,
  type FirstDecision,
  isTerminalFirstDecision,
} from "@/lib/experience/labels";

/// 日期下限（后端 `TOO_EARLY`）
export const MIN_DATE = "1990-01-01";

const SHANGHAI_DATE = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Shanghai",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/// 北京时间的今天 `yyyy-MM-dd`：后端按北京时间判「不晚于今天」，表单的 `max` 与校验跟它一致
export function todayInShanghai(now: number): string {
  return SHANGHAI_DATE.format(now);
}

/// 表单联动（spec 6.3）。与后端的一致性规则一一对应：
/// - 直接拒稿、审后拒稿 → 最终只能是拒稿；首轮录用 → 只能是录用；这三种不填最终决定日期
/// - 最终结果进行中 → 不填最终决定日期
/// - 直接拒稿（没送审）→ 不填审稿轮数、审稿质量；审后拒稿、首轮录用 → 审稿轮数只能是 1
export interface FormConstraints {
  /// 首轮终局时自动选定的最终结果，其余选项禁用
  lockedFinalOutcome: FinalOutcome | null;
  /// `terminal`、`in-progress` 时禁用并清空
  finalDecisionOn: "enabled" | "terminal" | "in-progress";
  /// `none` 禁用并清空；`one` 固定为 1
  reviewRounds: "enabled" | "none" | "one";
  reviewQuality: "enabled" | "none";
}

export function constraintsFor(
  firstDecision: FirstDecision | "",
  finalOutcome: FinalOutcome | "",
): FormConstraints {
  if (firstDecision !== "" && isTerminalFirstDecision(firstDecision)) {
    const desk = firstDecision === "DESK_REJECT";
    return {
      lockedFinalOutcome: firstDecision === "ACCEPT" ? "ACCEPTED" : "REJECTED",
      finalDecisionOn: "terminal",
      reviewRounds: desk ? "none" : "one",
      reviewQuality: desk ? "none" : "enabled",
    };
  }
  return {
    lockedFinalOutcome: null,
    finalDecisionOn: finalOutcome === "IN_PROGRESS" ? "in-progress" : "enabled",
    reviewRounds: "enabled",
    reviewQuality: "enabled",
  };
}

/// 两个 `yyyy-MM-dd` 之间的天数，实现在日期输入工具里
export { daysBetween } from "@/lib/date-input";
