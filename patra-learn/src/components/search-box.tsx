// patra-learn/src/components/search-box.tsx
// 搜索（画板 Search）：大搜索框、类型分段 + 难度多选、命中高亮、常搜词；个人环境条目只给数量提示
"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { DIFFICULTIES, type Difficulty, type EntryType } from "@/lib/content/types";
import { DIFFICULTY_LABELS } from "@/lib/site/labels";
import { type SearchDoc, searchEntries } from "@/lib/site/search";
import { TypeTabs } from "./entry-list";
import { LiveMasteryChip } from "./progress-ui";
import { button, cardClass, cx, LevelBadge, meta, pill, TitleText, TypeBadge } from "./ui";

/// 把 [起, 止) 那一段包进高亮
function Marked({ text, mark }: { text: string; mark?: [number, number] }) {
  if (!mark) return <TitleText text={text} />;
  const [from, to] = mark;
  return (
    <>
      {text.slice(0, from)}
      <mark className="rounded-[3px] bg-mark px-px text-inherit">{text.slice(from, to)}</mark>
      {text.slice(to)}
    </>
  );
}

export function SearchBox({
  docs,
  hints,
  personalHref,
}: {
  docs: SearchDoc[];
  hints: string[];
  personalHref: string;
}) {
  const [query, setQuery] = useState("");
  const [type, setType] = useState<EntryType | "all">("all");
  const [levels, setLevels] = useState<Difficulty[]>([]);
  const found = useMemo(
    () => searchEntries(docs, query, { type, difficulties: levels }),
    [docs, query, type, levels],
  );
  const hasQuery = query.trim().length > 0;
  const filtered = found.hits - found.results.length;
  const pickHint = (h: string) => {
    setQuery(h);
    setType("all");
    setLevels([]);
  };
  const hintPills = (
    <div className="flex flex-wrap justify-center gap-2">
      {hints.map((h) => (
        <button key={h} type="button" onClick={() => pickHint(h)} className={pill()}>
          {h}
        </button>
      ))}
    </div>
  );

  return (
    <div className="flex flex-col gap-5.5">
      <label className="box-border flex h-15 items-center gap-3 rounded-2xl border-[1.5px] border-teal-mark bg-surface pr-3 pl-5 shadow-[0_0_0_5px_var(--color-teal-tint)]">
        <svg
          aria-hidden="true"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="#4A574F"
          strokeWidth="2"
          strokeLinecap="round"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.5-3.5" />
        </svg>
        <input
          // biome-ignore lint/a11y/noAutofocus: 搜索页只有这一个输入框，进来就是要输入
          autoFocus
          type="search"
          aria-label="搜索条目"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="搜条目、概念、报错，比如：事务、索引、Nacos"
          className="min-w-0 flex-1 border-0 bg-transparent font-body text-[19px] text-ink outline-none placeholder:text-ink-4 max-sm:text-base"
        />
        {hasQuery ? (
          <button type="button" onClick={() => setQuery("")} className={button("quiet", "sm")}>
            清空
          </button>
        ) : null}
      </label>

      <div className="flex flex-wrap items-center gap-3">
        <TypeTabs value={type} onChange={setType} />
        <span className="flex-1" />
        <span className={meta}>难度</span>
        {DIFFICULTIES.map((d) => {
          const on = levels.includes(d);
          return (
            <button
              key={d}
              type="button"
              aria-pressed={on}
              onClick={() => setLevels(on ? levels.filter((l) => l !== d) : [...levels, d])}
              className={pill(on)}
            >
              <LevelBadge difficulty={d} bare />
              {DIFFICULTY_LABELS[d]}
            </button>
          );
        })}
      </div>

      {hasQuery ? (
        <div className={meta} aria-live="polite">
          {found.results.length > 0
            ? `找到 ${found.results.length} 篇${filtered > 0 ? `，筛掉了 ${filtered} 篇` : ""} · 搜的是标题、一句话摘要、标签和所在路线`
            : found.hits > 0
              ? `找到 ${found.hits} 篇，但都被上面的筛选挡住了`
              : ""}
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <span className="font-semibold font-ui text-ink-3 text-meta">大家常搜</span>
          <div className="flex flex-wrap gap-2">
            {hints.map((h) => (
              <button key={h} type="button" onClick={() => pickHint(h)} className={pill()}>
                {h}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="flex flex-col gap-2.5">
        {found.results.map(({ doc, titleMark, summaryMark }) => (
          <Link
            key={doc.slug}
            href={`/entries/${doc.slug}`}
            className={cx(cardClass(true), "flex flex-col gap-1.5 px-5 py-4")}
          >
            <span className="flex flex-wrap items-center gap-2">
              <TypeBadge type={doc.type} />
              <LevelBadge difficulty={doc.difficulty} />
              {doc.routes[0] ? <span className={meta}>{doc.routes[0]}</span> : null}
              <span className="flex-1" />
              <LiveMasteryChip slug={doc.slug} />
            </span>
            <span className="font-bold font-display text-h3 text-ink leading-[1.45]">
              <Marked text={doc.title} {...(titleMark ? { mark: titleMark } : {})} />
            </span>
            <span className="font-ui text-ink-3 text-ui leading-[1.6]">
              <Marked text={doc.summary} {...(summaryMark ? { mark: summaryMark } : {})} />
            </span>
          </Link>
        ))}
      </div>

      {hasQuery && found.hits === 0 ? (
        <div className="flex flex-col items-center gap-3.5 rounded-card border border-line bg-surface-soft px-7 py-9 text-center">
          <span className="font-bold font-display text-[20px] text-ink">
            没找到「{query.trim()}」
          </span>
          <span className="font-ui text-ink-3 text-ui">学习站还没写到它，或者换个说法试试。</span>
          {hintPills}
        </div>
      ) : null}

      {found.personalCount > 0 ? (
        <div className={cx(meta, "flex flex-wrap items-center gap-1 px-0.5 py-1")}>
          另有 {found.personalCount} 篇「个人环境」条目也提到了它，只在
          <Link href={personalHref}>个人环境标签</Link>
          下显示。
        </div>
      ) : null}
    </div>
  );
}
