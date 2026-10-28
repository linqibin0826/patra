import { formatDateCn } from "@/lib/experience/format";
import {
  ARTICLE_TYPE_LABELS,
  FINAL_OUTCOME_LABELS,
  FIRST_DECISION_LABELS,
} from "@/lib/experience/labels";
import type { Revision } from "@/lib/interaction/experience-schema";

const UNSET = "未填";
const date = (value: string | null) => (value ? formatDateCn(value) : UNSET);
const score = (value: number | null) => (value === null ? UNSET : `${value} / 5`);

/// 修改历史里比较的 11 个结构化字段与它们的写法（正文另按词比较）
const FIELDS: { label: string; show: (revision: Revision) => string }[] = [
  { label: "投稿日期", show: (r) => date(r.submittedOn) },
  { label: "首轮决定日期", show: (r) => date(r.firstDecisionOn) },
  { label: "首轮结果", show: (r) => FIRST_DECISION_LABELS[r.firstDecision] },
  { label: "最终结果", show: (r) => FINAL_OUTCOME_LABELS[r.finalOutcome] },
  { label: "最终决定日期", show: (r) => date(r.finalDecisionOn) },
  { label: "文章类型", show: (r) => ARTICLE_TYPE_LABELS[r.articleType] },
  { label: "审稿轮数", show: (r) => (r.reviewRounds === null ? UNSET : String(r.reviewRounds)) },
  { label: "审稿质量", show: (r) => score(r.reviewQuality) },
  { label: "编辑沟通", show: (r) => score(r.editorCommunication) },
  { label: "APC", show: (r) => (r.apcPaid === null ? UNSET : r.apcPaid ? "付了" : "没付") },
  {
    label: "转投",
    show: (r) => (r.previouslyRejected === null ? UNSET : r.previouslyRejected ? "是" : "否"),
  },
];

/// 与上一版（时间上更早的那版）相比改了的字段：`字段：旧值 → 新值`
export function fieldChanges(
  previous: Revision,
  current: Revision,
): { label: string; from: string; to: string }[] {
  return FIELDS.flatMap(({ label, show }) => {
    const from = show(previous);
    const to = show(current);
    return from === to ? [] : [{ label, from, to }];
  });
}

/// 最初版本：只列各字段的值
export function fieldValues(revision: Revision): { label: string; value: string }[] {
  return FIELDS.map(({ label, show }) => ({ label, value: show(revision) }));
}
