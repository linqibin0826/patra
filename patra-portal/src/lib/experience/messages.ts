import { formatDateCn } from "@/lib/experience/format";
import type { ExperienceFailure } from "@/lib/interaction/experience-actions";

/// 投稿经验表单的文案（简报 §4.3，一致性规则按后端重写，spec 第 3 节）

/// 联动说明：禁用的字段下面写原因，服务端回 `INCONSISTENT` / `NOT_APPLICABLE` 时也用同一句
export const CONSTRAINT_HINTS = {
  finalOutcome: "首轮已是最终结果：直接拒稿、审后拒稿最终只能是拒稿，首轮录用只能是录用",
  finalDateTerminal: "首轮已是最终结果，不用填最终决定日期",
  finalDateInProgress: "稿件还在进行中，不用填最终决定日期",
  finalDateNotApplicable: "首轮已是最终结果或稿件还在进行中，不用填最终决定日期",
  notReviewed: "没有送审，不用填审稿轮数和审稿质量",
  oneRound: "首轮即结束，审稿只有 1 轮",
} as const;

export const REQUIRED_MESSAGES: Record<string, string> = {
  submittedOn: "请填写投稿日期",
  firstDecisionOn: "请填写首轮决定日期",
  firstDecision: "请选择首轮结果",
  finalOutcome: "请选择最终结果",
  articleType: "请选择文章类型",
};

export const DATE_MESSAGES = {
  format: "日期写成 2026-08-14 这样",
  future: "日期不能晚于今天",
  tooEarly: "日期不能早于 1990 年",
  beforeSubmission: "首轮决定日期不能早于投稿日期",
  beforeFirstDecision: "最终决定日期不能早于首轮决定日期",
} as const;

export const ROUNDS_MESSAGE = "审稿轮数填 1 到 10 之间的整数";
export const SCORE_MESSAGE = "评分是 1 到 5";
export const BODY_TOO_LONG = "正文最多 2,000 字";
export const BODY_TOO_MANY_BYTES = "内容太长了，请删减一些";
const INVALID_MESSAGE = "这一项的内容不对，请重新填写";

const FIELDS = new Set([
  "submittedOn",
  "firstDecisionOn",
  "finalDecisionOn",
  "firstDecision",
  "finalOutcome",
  "articleType",
  "reviewRounds",
  "reviewQuality",
  "editorCommunication",
  "apcPaid",
  "previouslyRejected",
  "body",
]);

/// 服务端 422 `errors[]` 的一项 → 字段下面的文案（spec 6.4）；认不出、或该走底部提示条（`DUPLICATE`）时为 null
export function fieldErrorMessage(field: string, code: string | null): string | null {
  if (!FIELDS.has(field)) {
    return null;
  }
  switch (code) {
    case "REQUIRED":
      return REQUIRED_MESSAGES[field] ?? INVALID_MESSAGE;
    case "FUTURE":
      return DATE_MESSAGES.future;
    case "TOO_EARLY":
      return DATE_MESSAGES.tooEarly;
    case "BEFORE_SUBMISSION":
      return DATE_MESSAGES.beforeSubmission;
    case "BEFORE_FIRST_DECISION":
      return DATE_MESSAGES.beforeFirstDecision;
    case "OUT_OF_RANGE":
      return field === "reviewRounds" ? ROUNDS_MESSAGE : SCORE_MESSAGE;
    case "TOO_LONG":
      return BODY_TOO_LONG;
    case "INVALID":
    case "INVALID_CHARACTER":
      return INVALID_MESSAGE;
    case "INCONSISTENT":
      return field === "reviewRounds" ? CONSTRAINT_HINTS.oneRound : CONSTRAINT_HINTS.finalOutcome;
    case "NOT_APPLICABLE":
      return field === "finalDecisionOn"
        ? CONSTRAINT_HINTS.finalDateNotApplicable
        : CONSTRAINT_HINTS.notReviewed;
    default:
      return null;
  }
}

type FailureMode = "create" | "edit" | "delete";

const RETRY_LATER: Record<FailureMode, string> = {
  create: "没有发布成功，请稍后再试。",
  edit: "没有保存成功，请稍后再试。",
  delete: "没有删除成功，请稍后再试",
};

/// 写操作失败时表单底部（或删除对话框里）的提示条（spec 4.3）。`submittedOn` 用于重复投稿日期那一句。
/// 前端正常操作到不了的（不是作者、认不出的字段错误）按「稍后再试」
export function experienceFailureMessage(
  reason: ExperienceFailure["reason"],
  mode: FailureMode,
  submittedOn: string,
): string {
  switch (reason) {
    case "duplicate":
      return `你已经写过 ${formatDateCn(submittedOn)}投这本期刊的经验，同一天的投稿只能写一条。`;
    case "daily-limit":
      return "今天已经发布了 10 条投稿经验，明天再来吧。";
    case "account-restricted":
      return mode === "create" ? "账号状态异常，暂时不能发布。" : "账号状态异常，暂时不能保存。";
    case "email-unverified":
      return "验证邮箱后才能写投稿经验。";
    case "journal-gone":
      return "这本期刊已不在 Patra 的索引里，不能再写投稿经验。";
    case "post-gone":
      return "这条投稿经验已被删除。";
    default:
      return RETRY_LATER[mode];
  }
}
