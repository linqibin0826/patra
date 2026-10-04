import { ExternalLink } from "lucide-react";
import Link from "next/link";
import { EvidenceBadge } from "@/components/portal/EvidenceBadge";
import { BookmarkButton } from "@/components/portal/paper-detail/BookmarkButton";
import { RichInlineText } from "@/components/portal/RichInlineText";
import { deriveByline, deriveFullText } from "@/lib/portal-api/publication-derive";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";
import type { PaperDetail } from "@/types/portal";

/// 文献题录头：等宽刊头行（类型 · 开放获取）→ 显示级标题（入场上浮）→ 原文标题 → 作者 · 刊名 · 年份 → 证据徽章与操作。
export function PaperHeader({ paper }: { paper: PaperDetail }) {
  const { shown, extra } = deriveByline(paper.authors);
  const fullText = deriveFullText(paper);
  const types = paper.publicationTypes.slice(0, 2);

  return (
    <header className="flex flex-col">
      {(types.length > 0 || paper.isOa) && (
        <div className="anim-fade mb-5 flex flex-wrap items-center gap-x-3 gap-y-2 font-mono text-2xs uppercase tracking-mono text-fg-3">
          {types.map((t, i) => (
            <span key={t} className="inline-flex items-center gap-3">
              {i > 0 && (
                <span aria-hidden className="text-ink-300">
                  ·
                </span>
              )}
              {t}
            </span>
          ))}
          {paper.isOa && (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-moss-500 bg-moss-50 px-2.5 py-0.5 text-status-success-ink">
              <span aria-hidden className="size-1.5 rounded-full bg-moss-500" />
              开放获取
            </span>
          )}
        </div>
      )}

      <h1 className="anim-rise m-0 font-serif text-display-3 leading-heading font-medium tracking-tight text-pretty text-fg-1">
        <RichInlineText text={paper.title} />
      </h1>
      {paper.originalTitle && (
        <p className="m-0 mt-3 font-serif text-xl leading-snug text-fg-3 italic">
          <RichInlineText text={paper.originalTitle} />
        </p>
      )}

      <div className="anim-rise mt-6 flex flex-wrap items-center gap-y-1 font-sans text-md leading-normal text-fg-2 [--delay:120ms]">
        {shown.map((a, i) => (
          <span key={a.order} className="whitespace-nowrap">
            {a.name}
            {a.corresponding && (
              <span className="ml-px font-semibold text-clay-600" title="通讯作者">
                {" "}
                ✉
              </span>
            )}
            {i < shown.length - 1 ? "、" : ""}
          </span>
        ))}
        {extra > 0 && <span className="text-fg-3">&nbsp;等 {extra} 位</span>}
        <span aria-hidden className="mx-3 inline-block size-[3px] rounded-full bg-ink-300" />
        {paper.venueId && paper.venueName ? (
          <Link
            href={`/journals/${paper.venueId}`}
            className="link-draw pb-px font-serif text-lg font-medium text-link italic hover:text-link-hover"
          >
            {paper.venueName}
          </Link>
        ) : (
          paper.venueName && (
            <span className="font-serif text-lg text-ink-800 italic">{paper.venueName}</span>
          )
        )}
        {paper.publicationYear != null && (
          <span className="tabular-nums text-fg-2">&nbsp;· {paper.publicationYear}</span>
        )}
      </div>

      <div className="anim-rise mt-7 flex flex-wrap items-center gap-3 [--delay:200ms]">
        <EvidenceBadge level={paper.evidenceLevel} />
        <div className="flex flex-wrap gap-2.5">
          {fullText.href ? (
            <a
              className={btnPrimary}
              href={fullText.href}
              target="_blank"
              rel="noopener noreferrer"
            >
              {fullText.label} <ExternalLink size={14} data-icon="trailing" />
            </a>
          ) : (
            <button className={`${btnSecondary} opacity-55`} type="button" disabled>
              {fullText.label}
            </button>
          )}
          <BookmarkButton paperId={paper.id} />
        </div>
      </div>
    </header>
  );
}
