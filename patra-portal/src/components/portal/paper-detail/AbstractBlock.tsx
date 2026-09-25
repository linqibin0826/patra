import { RichInlineText } from "@/components/portal/RichInlineText";
import { deriveAbstract } from "@/lib/portal-api/publication-derive";
import type { PaperDetail } from "@/types/portal";

/** 摘要正文段：统一排版 + 白名单富文本渲染（三处共用）。 */
function BodyText({ text }: { text: string }) {
  return (
    // 左对齐 + text-wrap:pretty：断行更均匀、消孤行，右缘参差显著缓解；不用 justify（浏览器贪心断行会拉出不均匀词距，WCAG 亦不建议）
    <p className="m-0 text-pretty font-serif text-lg leading-relaxed text-ink-800">
      <RichInlineText text={text} />
    </p>
  );
}

export function AbstractBlock({ paper }: { paper: PaperDetail }) {
  const abstract = deriveAbstract(paper);
  if (abstract.kind === "empty") {
    return (
      <div className="rounded-md border border-dashed border-border-default bg-paper-100 p-5 text-center font-sans text-md italic text-fg-3">
        暂无摘要 · 该来源未提供结构化或纯文本摘要
      </div>
    );
  }
  if (abstract.kind === "plain") {
    return <BodyText text={abstract.text} />;
  }
  return (
    <div>
      {abstract.sections.map((s, i) => {
        // 静态摘要段落列表（无状态、不重排），label 可能重复或为 null，用 index 复合键保唯一
        const key = `${s.label ?? "unlabeled"}-${i}`;
        if (s.label === null) {
          return (
            <div key={key} className="border-t border-border-subtle py-3.5 first:border-t-0">
              <BodyText text={s.text} />
            </div>
          );
        }
        return (
          <div
            key={key}
            className="grid grid-cols-[92px_1fr] gap-[18px] border-t border-border-subtle py-3.5 first:border-t-0 max-[640px]:grid-cols-1 max-[640px]:gap-[5px]"
          >
            <div className="pt-1 font-mono text-3xs font-medium uppercase tracking-mono text-clay-700">
              {s.label}
            </div>
            <BodyText text={s.text} />
          </div>
        );
      })}
    </div>
  );
}
