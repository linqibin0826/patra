import { ARTICLE_TYPE_LABELS } from "@/lib/experience/labels";
import type { Experience } from "@/lib/interaction/experience-schema";

/// 经验页的结构化字段（简报 §3.16）：两列定义表（≤ 880px 单列），只列填了的选填项；文章类型总在
export function ExperienceFacts({ experience }: { experience: Experience }) {
  const facts: [string, string][] = [["文章类型", ARTICLE_TYPE_LABELS[experience.articleType]]];
  if (experience.reviewRounds !== null) facts.push(["审稿轮数", String(experience.reviewRounds)]);
  if (experience.reviewQuality !== null)
    facts.push(["审稿质量", `${experience.reviewQuality} / 5`]);
  if (experience.editorCommunication !== null) {
    facts.push(["编辑沟通", `${experience.editorCommunication} / 5`]);
  }
  if (experience.apcPaid !== null) facts.push(["APC", experience.apcPaid ? "付了" : "没付"]);
  if (experience.previouslyRejected !== null) {
    facts.push(["转投", experience.previouslyRejected ? "是" : "否"]);
  }
  return (
    <dl className="m-0 grid grid-cols-[max-content_minmax(0,1fr)_max-content_minmax(0,1fr)] gap-x-6 gap-y-3 max-[880px]:grid-cols-[max-content_minmax(0,1fr)]">
      {facts.map(([term, value]) => (
        <div key={term} className="contents">
          <dt className="whitespace-nowrap pt-0.75 font-mono text-2xs text-fg-3 tracking-mono">
            {term}
          </dt>
          <dd className="m-0 text-fg-1 text-md tabular-nums">{value}</dd>
        </div>
      ))}
    </dl>
  );
}
