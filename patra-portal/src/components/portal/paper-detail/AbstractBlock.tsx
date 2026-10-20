import { RailRow } from "@/components/portal/RailRow";
import { RichInlineText } from "@/components/portal/RichInlineText";
import { deriveAbstract } from "@/lib/portal-api/publication-derive";
import type { PaperDetail } from "@/types/portal";

/** 结构化摘要标签的中文译名（摘要是 PubMed 元数据，不受原文不改写的约束）。 */
export const ABSTRACT_LABEL_ZH: Record<string, string> = {
  BACKGROUND: "背景",
  OBJECTIVE: "目的",
  OBJECTIVES: "目的",
  AIM: "目的",
  AIMS: "目的",
  PURPOSE: "目的",
  METHODS: "方法",
  RESULTS: "结果",
  CONCLUSION: "结论",
  CONCLUSIONS: "结论",
  INTRODUCTION: "引言",
  DISCUSSION: "讨论",
};

/** 摘要正文段：统一排版 + 白名单富文本渲染。 */
function BodyText({ text }: { text: string }) {
  return (
    // 左对齐 + text-wrap:pretty：断行更均匀、消孤行，右缘参差显著缓解；不用 justify（浏览器贪心断行会拉出不均匀词距，WCAG 亦不建议）
    <p className="m-0 max-w-[36em] text-pretty font-serif text-lg leading-reading text-ink-800">
      <RichInlineText text={text} />
    </p>
  );
}

function SectionLabel({ label }: { label: string }) {
  const zh = ABSTRACT_LABEL_ZH[label.toUpperCase()];
  return (
    <div className="pt-1.5">
      <div className="font-mono text-2xs font-medium uppercase leading-snug tracking-mono text-clay-700">
        {label}
      </div>
      {zh && <div className="mt-0.5 font-sans text-xs text-fg-3">{zh}</div>}
    </div>
  );
}

/// 摘要（spec §5.4）：每段一个标签栏行，标签格放原标签与中文译名；纯文本摘要标签格留空。
export function AbstractBlock({ paper }: { paper: PaperDetail }) {
  const abstract = deriveAbstract(paper);
  if (abstract.kind === "empty") {
    return (
      <div className="rounded-lg border border-dashed border-border-default bg-paper-50 p-6 text-center font-sans text-md text-fg-3">
        暂无摘要 · 该来源未提供结构化或纯文本摘要
      </div>
    );
  }
  if (abstract.kind === "plain") {
    return (
      <RailRow>
        <BodyText text={abstract.text} />
      </RailRow>
    );
  }
  return (
    <div className="flex flex-col">
      {abstract.sections.map((s, i) => (
        <RailRow
          // biome-ignore lint/suspicious/noArrayIndexKey: 静态摘要段落列表（无状态、不重排），label 可能重复或为 null，用 index 复合键保唯一
          key={`${s.label ?? "unlabeled"}-${i}`}
          label={s.label ? <SectionLabel label={s.label} /> : undefined}
          className="border-t border-border-subtle py-6 first:border-t-0 first:pt-0"
        >
          <BodyText text={s.text} />
        </RailRow>
      ))}
    </div>
  );
}
