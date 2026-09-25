import { ExternalLink, Sparkles } from "lucide-react";
import Image from "next/image";
import { BookmarkButton } from "@/components/portal/paper-detail/BookmarkButton";
import { deriveEvidence, deriveFullText } from "@/lib/portal-api/publication-derive";
import { btnBlock, btnPrimary, btnSecondary } from "@/lib/portal-ui";
import type { PaperDetail } from "@/types/portal";

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
      <div className="rounded-lg border border-border-default bg-paper-50 p-4">
        <div className="mb-3 font-mono text-[9.5px] uppercase tracking-[0.06em] text-fg-3">
          操作
        </div>
        <div className="flex flex-col gap-2">
          {fullText.href ? (
            <a
              className={`${btnPrimary} ${btnBlock}`}
              href={fullText.href}
              target="_blank"
              rel="noopener noreferrer"
            >
              {fullText.label} <ExternalLink size={14} />
            </a>
          ) : (
            <button className={`${btnSecondary} ${btnBlock} opacity-55`} type="button" disabled>
              {fullText.label}
            </button>
          )}
          <BookmarkButton paperId={paper.id} block />
        </div>
      </div>

      <div className="rounded-lg border border-clay-200 bg-clay-50 p-4">
        <div className="mb-2.5 flex items-center gap-2 font-mono text-[9.5px] uppercase tracking-[0.06em] text-fg-3">
          <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={4} height={13} /> AI 速读
          <span className="ml-auto rounded-sm border border-clay-200 bg-paper-50 px-1.5 py-px text-[9px] normal-case text-clay-700">
            mock · 占位
          </span>
        </div>
        {paper.aiSummary ? (
          <p className="m-0 font-serif text-sm leading-normal text-ink-800">{paper.aiSummary}</p>
        ) : (
          <p className="m-0 font-sans text-sm italic text-fg-3">尚未生成 AI 速读。</p>
        )}
        <button
          type="button"
          disabled
          title="AI 速读为本版占位（mock），未接真实模型"
          className="mt-3 inline-flex cursor-not-allowed items-center gap-1.5 rounded-md border border-clay-200 bg-paper-50 px-2.5 py-1.5 font-sans text-sm text-clay-700 opacity-70"
        >
          <Sparkles size={13} aria-hidden /> {paper.aiSummary ? "重新生成" : "生成速读"}
        </button>
      </div>

      <div className="rounded-lg border border-border-default bg-paper-50 p-4">
        <div className="mb-3 font-mono text-[9.5px] uppercase tracking-[0.06em] text-fg-3">
          速览
        </div>
        <div className="flex flex-col">
          {stats.map((s) => (
            <div
              key={s.k}
              className="flex items-baseline justify-between gap-3 border-t border-border-subtle py-2.5 first:border-t-0"
            >
              <span className="font-sans text-sm text-fg-3">{s.k}</span>
              <span className="font-mono text-md font-medium tabular-nums text-ink-900">{s.v}</span>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}
