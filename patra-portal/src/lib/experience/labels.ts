/// 投稿经验的枚举与中文（PAP-122 交接 3.1）。顺序就是表单里选项的顺序

export const FIRST_DECISIONS = [
  "DESK_REJECT",
  "REJECT_AFTER_REVIEW",
  "MAJOR_REVISION",
  "MINOR_REVISION",
  "ACCEPT",
] as const;
export type FirstDecision = (typeof FIRST_DECISIONS)[number];

export const FINAL_OUTCOMES = ["ACCEPTED", "REJECTED", "WITHDRAWN", "IN_PROGRESS"] as const;
export type FinalOutcome = (typeof FINAL_OUTCOMES)[number];

export const ARTICLE_TYPES = [
  "ORIGINAL_RESEARCH",
  "REVIEW",
  "SYSTEMATIC_REVIEW",
  "CASE_REPORT",
  "SHORT_COMMUNICATION",
  "OTHER",
] as const;
export type ArticleType = (typeof ARTICLE_TYPES)[number];

export const FIRST_DECISION_LABELS: Record<FirstDecision, string> = {
  DESK_REJECT: "直接拒稿",
  REJECT_AFTER_REVIEW: "审后拒稿",
  MAJOR_REVISION: "大修",
  MINOR_REVISION: "小修",
  ACCEPT: "录用",
};

export const FINAL_OUTCOME_LABELS: Record<FinalOutcome, string> = {
  ACCEPTED: "录用",
  REJECTED: "拒稿",
  WITHDRAWN: "撤稿",
  IN_PROGRESS: "进行中",
};

export const ARTICLE_TYPE_LABELS: Record<ArticleType, string> = {
  ORIGINAL_RESEARCH: "原创研究",
  REVIEW: "综述",
  SYSTEMATIC_REVIEW: "系统综述 / Meta 分析",
  CASE_REPORT: "病例报告",
  SHORT_COMMUNICATION: "短篇通讯",
  OTHER: "其他",
};

/// 首轮就是最终结果的三种（直接拒稿、审后拒稿、首轮录用）
export function isTerminalFirstDecision(decision: FirstDecision): boolean {
  return decision === "DESK_REJECT" || decision === "REJECT_AFTER_REVIEW" || decision === "ACCEPT";
}
