import { ExternalLink, Sparkles } from "lucide-react";
import { BookmarkButton } from "@/components/portal/paper-detail/BookmarkButton";
import { deriveEvidence, deriveFullText } from "@/lib/portal-api/publication-derive";
import { btnBlock, btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";
import type { PaperDetail } from "@/types/portal";

const CARD = "rounded-xl border border-border-default bg-paper-50 p-5";
const CARD_LABEL =
  "mb-4 flex items-center gap-2 font-mono text-2xs uppercase tracking-mono text-fg-3";

/// 文献详情右侧栏：操作（去全文 / 收藏）→ AI 速读（尚未上线：后端 aiSummary 恒为 null）→ 速览数据。
export function PaperRail({ paper }: { paper: PaperDetail }) {
  const fullText = deriveFullText(paper);
  const ev = deriveEvidence(paper.evidenceLevel);
  const stats: { k: string; v: string }[] = [
    { k: "证据等级", v: ev.label },
    { k: "被引", v: (paper.citationCount ?? 0).toLocaleString() },
    { k: "来源", v: paper.source ?? "—" },
    { k: "收藏", v: (paper.bookmarks ?? 0).toLocaleString() },
    { k: "原文阅读", v: paper.estimatedReadMin != null ? `≈ ${paper.estimatedReadMin} 分钟` : "—" },
  ];

  return (
    <>
      {/* 单栏布局时侧栏落到正文之后，题录头已有同样的操作按钮，不再重复 */}
      <div className={`${CARD} max-[980px]:hidden`}>
        <div className={CARD_LABEL}>操作</div>
        <div className="flex flex-col gap-2.5">
          {fullText.href ? (
            <a
              className={cn(btnPrimary, btnBlock)}
              href={fullText.href}
              target="_blank"
              rel="noopener noreferrer"
            >
              {fullText.label} <ExternalLink size={14} data-icon="trailing" />
            </a>
          ) : (
            <button className={cn(btnSecondary, btnBlock, "opacity-55")} type="button" disabled>
              {fullText.label}
            </button>
          )}
          <BookmarkButton paperId={paper.id} block />
        </div>
      </div>

      <div className="rounded-xl border border-clay-200 bg-clay-50 p-5">
        <div className={CARD_LABEL}>
          <span aria-hidden className="h-3 w-1 rounded-[1px] bg-clay-600" />
          AI 速读
          <span className="ml-auto rounded-full border border-clay-200 bg-paper-50 px-2 py-px text-3xs normal-case text-clay-700">
            即将上线
          </span>
        </div>
        {paper.aiSummary ? (
          <p className="m-0 font-serif text-md leading-relaxed text-ink-800">{paper.aiSummary}</p>
        ) : (
          <p className="m-0 font-serif text-md text-fg-3 italic">尚未生成 AI 速读。</p>
        )}
        <button
          type="button"
          disabled
          title="功能即将上线"
          className="mt-4 inline-flex cursor-not-allowed items-center gap-1.5 rounded-full border border-clay-200 bg-paper-50 px-3 py-1.5 font-sans text-sm text-clay-700 opacity-70"
        >
          <Sparkles size={13} aria-hidden /> {paper.aiSummary ? "重新生成" : "生成速读"}
        </button>
      </div>

      <div className={CARD}>
        <div className={CARD_LABEL}>速览</div>
        <dl className="m-0 flex flex-col">
          {stats.map((s) => (
            <div
              key={s.k}
              className="flex items-baseline justify-between gap-3 border-t border-border-subtle py-3 first:border-t-0 first:pt-0 last:pb-0"
            >
              <dt className="font-sans text-sm text-fg-3">{s.k}</dt>
              <dd className="m-0 text-right font-sans text-md font-semibold tabular-nums text-ink-900">
                {s.v}
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </>
  );
}
