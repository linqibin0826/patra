// patra-learn/src/components/notes-view.tsx
// 「我的批注」页（画布「学习站批注」Notes 板）：按条目分组列出浏览器里存的批注，按类型筛选；
// 「有问题」整理成一段 Markdown 复制给 Claude，复制成功后标成导出过
"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  type Annotation,
  type AnnotationKind,
  exportable,
  exportMarkdown,
} from "@/lib/annotations/annotations";
import { useAnnotations } from "@/lib/annotations/use-annotations";
import type { EntryType } from "@/lib/content/types";
import { useHydrated } from "@/lib/progress/use-progress";
import { KindChip, Toast } from "./annotations";
import { button, cardClass, cx, meta, pill, TypeBadge } from "./ui";

/// 提示停留多久（毫秒），同掌握度
const TOAST_MS = 2600;

export interface NotesEntry {
  title: string;
  type: EntryType;
}

type Filter = "all" | AnnotationKind;

const FILTERS: { value: Filter; label: string }[] = [
  { value: "all", label: "全部" },
  { value: "question", label: "有问题" },
  { value: "note", label: "笔记" },
];

function monthDay(time: number) {
  const date = new Date(time);
  return `${String(date.getMonth() + 1).padStart(2, "0")}/${String(date.getDate()).padStart(2, "0")}`;
}

function Item({ a, onRemove }: { a: Annotation; onRemove: () => void }) {
  return (
    <article
      aria-label={a.text || a.quote.exact}
      className={cx(
        cardClass(),
        "flex flex-col gap-2.5 px-4.5 py-4",
        a.orphaned && "bg-surface-soft shadow-none",
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        <KindChip kind={a.kind} />
        {a.orphaned ? (
          <span className="inline-flex h-[22px] items-center rounded-chip bg-line-soft px-2 font-semibold font-ui text-ink-3 text-tiny">
            原文已变
          </span>
        ) : null}
        {a.exportedAt ? (
          <span className="inline-flex h-[22px] items-center rounded-chip border border-line bg-surface px-2 font-ui text-ink-4 text-tiny">
            {monthDay(a.exportedAt)} 导出过
          </span>
        ) : null}
        <span className={meta}>{monthDay(a.createdAt)} 记</span>
      </div>
      <p className="m-0 rounded-lg bg-line-soft px-2.5 py-2 text-[13.5px] text-ink-3 leading-[1.65]">
        {`“${a.quote.exact}”`}
      </p>
      <p className="m-0 whitespace-pre-line font-ui text-[14.5px] text-ink leading-[1.65]">
        {a.text || "（没写说明）"}
      </p>
      {a.orphaned ? (
        <p className={cx(meta, "m-0")}>
          这段原文后来改过，批注挂不回原处了。多半是问题已经改掉，看一眼原文再删。
        </p>
      ) : null}
      <div className="-mx-3 -mb-1.5 flex flex-wrap gap-1">
        <Link href={`/entries/${a.slug}`} className={button("quiet", "sm")}>
          {a.orphaned ? "看看原文现在怎么写" : "回到原文"}
        </Link>
        <button
          type="button"
          onClick={onRemove}
          className={cx(button("quiet", "sm"), "text-wrong hover:bg-wrong-bg")}
        >
          删除
        </button>
      </div>
    </article>
  );
}

function ExportDialog({
  markdown,
  count,
  exportedCount,
  includeExported,
  onIncludeExported,
  onCopy,
  onClose,
}: {
  markdown: string;
  count: number;
  exportedCount: number;
  includeExported: boolean;
  onIncludeExported: (value: boolean) => void;
  onCopy: () => void;
  onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/35 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="export-title"
        onKeyDown={(event) => {
          if (event.key === "Escape") onClose();
        }}
        className="box-border flex max-h-[calc(100vh-32px)] w-full max-w-[640px] flex-col gap-3.5 overflow-auto rounded-2xl bg-surface p-5.5 font-ui shadow-lift"
      >
        <div className="flex items-center justify-between gap-3">
          <h2 id="export-title" className="m-0 font-bold font-display text-h3 text-ink">
            导出给 Claude
          </h2>
          <button
            type="button"
            aria-label="关闭"
            onClick={onClose}
            className="inline-flex size-8 cursor-pointer items-center justify-center rounded-lg text-ink-4 hover:bg-line-soft hover:text-ink"
          >
            <svg
              aria-hidden="true"
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
            >
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
        <p className="m-0 text-ink-3 text-ui leading-[1.6]">
          复制下面这段，贴到和 Claude 的对话里。它会逐条对照原文，把讲错的改掉、讲不清的补上。
        </p>
        {exportedCount > 0 ? (
          <label className="inline-flex cursor-pointer items-center gap-2 text-ink-3 text-sm">
            <input
              type="checkbox"
              checked={includeExported}
              onChange={(event) => onIncludeExported(event.target.checked)}
              className="m-0 size-4 accent-teal"
            />
            也带上导出过的（{exportedCount} 条）
          </label>
        ) : null}
        <pre
          data-testid="export-preview"
          className="m-0 whitespace-pre-wrap break-words rounded-[10px] bg-ink px-4 py-3.5 font-mono text-[12.5px] text-code-ink leading-[1.75]"
        >
          {markdown}
        </pre>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className={meta}>复制后，这 {count} 条会标成导出过</span>
          <div className="flex gap-2">
            <button type="button" className={button("normal", "sm")} onClick={onClose}>
              取消
            </button>
            <button
              type="button"
              className={button("primary", "sm")}
              onClick={onCopy}
              disabled={count === 0}
            >
              复制
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export function NotesView({ entries }: { entries: Readonly<Record<string, NotesEntry>> }) {
  const { annotations, remove, markExported } = useAnnotations();
  // 服务端与水合那一遍是空列表，先不显示「还没有批注」，免得有批注的读者看到一闪
  const hydrated = useHydrated();
  const [filter, setFilter] = useState<Filter>("all");
  const [dialog, setDialog] = useState(false);
  const [includeExported, setIncludeExported] = useState(false);
  const [toast, setToast] = useState("");

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(""), TOAST_MS);
    return () => clearTimeout(t);
  }, [toast]);

  const pending = exportable(annotations, false);
  const exportedCount = exportable(annotations, true).length - pending.length;
  const picked = exportable(annotations, includeExported);
  const titles = Object.fromEntries(Object.entries(entries).map(([slug, e]) => [slug, e.title]));
  // 服务端没有 window：只在打开导出时生成
  const markdown = dialog ? exportMarkdown(picked, titles, window.location.origin, new Date()) : "";

  const shown = annotations.filter((a) => filter === "all" || a.kind === filter);
  const groups = [...new Set(shown.map((a) => a.slug))].map((slug) => ({
    slug,
    entry: entries[slug],
    items: shown.filter((a) => a.slug === slug),
  }));

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(markdown);
    } catch {
      setToast("没能复制，请在上面手动选中复制");
      return;
    }
    markExported(picked.map((a) => a.id));
    setDialog(false);
    setToast(`已复制 ${picked.length} 条 · 去和 Claude 的对话里粘贴`);
  };

  return (
    <div className="mx-auto flex max-w-[760px] flex-col gap-7">
      <header className="flex flex-col gap-2.5">
        <h1 className="m-0 font-bold font-display text-h1 text-ink leading-[1.3]">我的批注</h1>
        <p className="m-0 font-ui text-ink-3 text-ui leading-[1.6]">
          读条目时选中文字记下的问题和笔记，只存在这个浏览器里。
        </p>
      </header>

      {!hydrated ? null : annotations.length === 0 ? (
        <div
          className={cx(cardClass(), "flex flex-col items-center gap-3.5 px-7 py-10 text-center")}
        >
          <div className="font-bold font-display text-h3 text-ink">还没有批注</div>
          <p className="m-0 max-w-[420px] font-ui text-ink-3 text-ui leading-[1.6]">
            读条目时选中一段文字，点「有问题」或「记笔记」。有问题的那些可以一起导出给
            Claude，让它照着改条目。
          </p>
          <Link href="/routes" className={button("normal", "sm")}>
            去读一篇
          </Link>
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <fieldset className="m-0 flex flex-wrap gap-2 border-0 p-0">
              <legend className="sr-only">按类型筛选</legend>
              {FILTERS.map((f) => {
                const n = annotations.filter((a) => f.value === "all" || a.kind === f.value).length;
                return (
                  <button
                    key={f.value}
                    type="button"
                    aria-pressed={filter === f.value}
                    onClick={() => setFilter(f.value)}
                    className={cx(pill(filter === f.value), "cursor-pointer")}
                  >
                    {f.label} <span className="text-ink-4 text-xs">{n}</span>
                  </button>
                );
              })}
            </fieldset>
            <button
              type="button"
              className={button("primary", "sm")}
              disabled={pending.length === 0 && exportedCount === 0}
              onClick={() => {
                setIncludeExported(pending.length === 0);
                setDialog(true);
              }}
            >
              导出给 Claude · {pending.length} 条
            </button>
          </div>

          {groups.map(({ slug, entry, items }) => (
            <section key={slug} aria-label={entry?.title ?? slug} className="flex flex-col gap-3">
              <div className="flex flex-wrap items-center gap-2.5">
                {entry ? <TypeBadge type={entry.type} /> : null}
                <Link href={`/entries/${slug}`} className="font-bold font-display text-h3 text-ink">
                  {entry?.title ?? slug}
                </Link>
              </div>
              {items.map((a) => (
                <Item key={a.id} a={a} onRemove={() => remove(a.id)} />
              ))}
            </section>
          ))}
        </>
      )}

      {dialog ? (
        <ExportDialog
          markdown={markdown}
          count={picked.length}
          exportedCount={exportedCount}
          includeExported={includeExported}
          onIncludeExported={setIncludeExported}
          onCopy={copy}
          onClose={() => setDialog(false)}
        />
      ) : null}
      {toast ? <Toast text={toast} /> : null}
    </div>
  );
}
