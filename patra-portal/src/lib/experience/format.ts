import {
  FINAL_OUTCOME_LABELS,
  FIRST_DECISION_LABELS,
  type FinalOutcome,
  type FirstDecision,
  isTerminalFirstDecision,
} from "@/lib/experience/labels";
import { formatDate, formatMonthDay } from "@/lib/format-time";

/// 投稿经验与统计的时长、日期写法（简报 §3.22）。日期都是 `yyyy-MM-dd` 字符串，按字符串拆分，不经 `Date`
/// （避免按时区换算后差一天）

/// `42 天`
export function formatDays(days: number): string {
  return `${days} 天`;
}

/// 满 28 天时次要位置补的 `约 6 周`（四舍五入到整周）；不满 28 天没有
export function formatWeeksHint(days: number): string | null {
  return days >= 28 ? `约 ${Math.round(days / 7)} 周` : null;
}

/// P25–P75：`28–63 天`
export function formatRange(low: number, high: number): string {
  return `${low}–${high} 天`;
}

function parts(date: string): [number, number, number] {
  const [year = "", month = "", day = ""] = date.split("-");
  return [Number(year), Number(month), Number(day)];
}

/// `2026 年 3 月 2 日`
export function formatDateCn(date: string): string {
  const [year, month, day] = parts(date);
  return `${year} 年 ${month} 月 ${day} 日`;
}

/// `2026 年 3 月投稿`
export function formatSubmittedMonth(date: string): string {
  const [year, month] = parts(date);
  return `${year} 年 ${month} 月投稿`;
}

/// 结果行：`首轮 42 天 · 大修  →  录用`；首轮就是最终（直接拒稿、审后拒稿、首轮录用）时不写箭头
export function formatOutcomeLine(experience: {
  firstRoundDays: number;
  firstDecision: FirstDecision;
  finalOutcome: FinalOutcome;
}): string {
  const first = `首轮 ${formatDays(experience.firstRoundDays)} · ${FIRST_DECISION_LABELS[experience.firstDecision]}`;
  return isTerminalFirstDecision(experience.firstDecision)
    ? first
    : `${first}  →  ${FINAL_OUTCOME_LABELS[experience.finalOutcome]}`;
}

/// 百分比取整：`47%`
export function formatPercent(rate: number): string {
  return `${Math.round(rate * 100)}%`;
}

/// 汇总卡底部的口径行
export function summaryScopeLine(count: number, window: "3y" | "all"): string {
  // 全角括号后面不再空一格（简报 §3.13 原文）
  const scope = window === "3y" ? "近 3 年（按投稿日期）· " : "全部 · ";
  return `${count} 条经验 · ${scope}首轮用时不含直接拒稿`;
}

/// 列表行第三行：投稿到最终决定的用时（首轮即最终、没填最终日期时不写）、审稿轮数、两个评分、APC、转投。
/// 没填的项不出现；全没填时是空串（这一行不渲染）
export function formatDetailLine(experience: {
  firstDecision: FirstDecision;
  finalOutcome: FinalOutcome;
  totalDays: number | null;
  reviewRounds: number | null;
  reviewQuality: number | null;
  editorCommunication: number | null;
  apcPaid: boolean | null;
  previouslyRejected: boolean | null;
}): string {
  const parts: string[] = [];
  if (experience.totalDays !== null && !isTerminalFirstDecision(experience.firstDecision)) {
    parts.push(
      `投稿到${FINAL_OUTCOME_LABELS[experience.finalOutcome]} ${formatDays(experience.totalDays)}`,
    );
  }
  if (experience.reviewRounds !== null) {
    parts.push(`审稿 ${experience.reviewRounds} 轮`);
  }
  if (experience.reviewQuality !== null) {
    parts.push(`审稿质量 ${experience.reviewQuality}/5`);
  }
  if (experience.editorCommunication !== null) {
    parts.push(`编辑沟通 ${experience.editorCommunication}/5`);
  }
  if (experience.apcPaid !== null) {
    parts.push(experience.apcPaid ? "付了 APC" : "没付 APC");
  }
  if (experience.previouslyRejected !== null) {
    parts.push(experience.previouslyRejected ? "转投过" : "未转投");
  }
  return parts.join(" · ");
}

/// 发布日期：今年 `6 月 3 日`，往年 `2025 年 3 月 2 日`（北京时间）
export function formatPublished(iso: string, now: number): string {
  const sameYear =
    formatDate(iso).slice(0, 4) === formatDate(new Date(now).toISOString()).slice(0, 4);
  return sameYear ? formatMonthDay(iso) : formatDate(iso);
}
