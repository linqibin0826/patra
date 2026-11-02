"use client";

import { BookOpen, X } from "lucide-react";
import Link from "next/link";
import type { Ref } from "react";
import { RichInlineText } from "@/components/portal/RichInlineText";
import type { HistoryEntry } from "@/lib/analytics/history-schema";
import { historyTime } from "@/lib/history/groups";
import { inlinePlainText } from "@/lib/portal-api/title-derive";
import { parseInlineMarkup } from "@/lib/rich-inline-text";
import { cn } from "@/lib/utils";

/// 「已不在」的文献在删除按钮读屏名里的说法
const GONE = "这篇文献已不在 Patra 上";

/// 删除按钮的读屏名：标题去掉内联标签后的前 40 个字（按码点，不切坏字符）
function deleteLabel(entry: HistoryEntry): string {
  const title = entry.paper ? inlinePlainText(parseInlineMarkup(entry.paper.title)) : GONE;
  return `从阅读历史删除：${Array.from(title).slice(0, 40).join("")}`;
}

/// 阅读历史的一行（简报 v0.18 §3.7 第 4 条）：时间 · 正文 · 删除。删除按钮一直显示；删除中整行变淡、按钮不可点
export function HistoryRow({
  entry,
  deleting,
  failed,
  onDelete,
  deleteRef,
}: {
  entry: HistoryEntry;
  deleting: boolean;
  failed: boolean;
  onDelete: () => void;
  deleteRef: Ref<HTMLButtonElement>;
}) {
  const { paper } = entry;
  return (
    <article
      className={cn(
        "grid grid-cols-[56px_minmax(0,1fr)_32px] items-start gap-4 border-b border-border-subtle py-4.5 max-md:grid-cols-[44px_minmax(0,1fr)_32px]",
        deleting && "opacity-35",
      )}
    >
      <time dateTime={entry.lastReadAt} className="pt-1 font-mono text-xs tabular-nums text-fg-3">
        {historyTime(entry.lastReadAt)}
      </time>
      <div className="flex min-w-0 flex-col gap-1.5">
        {paper ? (
          <>
            <h3 className="line-clamp-2 font-serif text-lg leading-[1.35] font-medium text-ink-900">
              <Link href={`/papers/${entry.articleId}`} className="link-draw">
                <RichInlineText text={paper.title} />
              </Link>
            </h3>
            <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-xs text-fg-2">
              {paper.journal && (
                <span className="font-serif text-md text-ink-700 italic">{paper.journal}</span>
              )}
              {paper.journal && paper.year && (
                <span aria-hidden="true" className="text-ink-300">
                  ·
                </span>
              )}
              {paper.year && <span className="tabular-nums">{paper.year}</span>}
              {entry.fulltextRead && (
                <span className="ml-1.5 inline-flex h-5 items-center gap-1 rounded-full bg-paper-200 px-2 text-2xs font-medium text-fg-2">
                  <BookOpen className="size-3" strokeWidth={1.75} aria-hidden="true" />
                  读过全文
                </span>
              )}
            </div>
          </>
        ) : (
          <h3 className="text-base text-fg-3">{GONE}</h3>
        )}
        {failed && (
          <p role="alert" className="mt-0.5 text-xs text-status-danger">
            没能删除，请重试。
          </p>
        )}
      </div>
      <button
        ref={deleteRef}
        type="button"
        onClick={onDelete}
        disabled={deleting}
        aria-label={deleteLabel(entry)}
        title="从阅读历史删除"
        className="inline-flex size-8 items-center justify-center rounded-full text-fg-3 not-disabled:hover:bg-paper-200 not-disabled:hover:text-ink-900"
      >
        <X className="size-4" strokeWidth={1.75} aria-hidden="true" />
      </button>
    </article>
  );
}
