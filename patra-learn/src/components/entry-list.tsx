// patra-learn/src/components/entry-list.tsx
// 标签页的条目卡片：按类型分段筛选（画板 Tag 右侧）
"use client";

import Link from "next/link";
import { useState } from "react";
import type { EntryType } from "@/lib/content/types";
import type { EntryCard } from "@/lib/site/entry-cards";
import { TYPE_LABELS } from "@/lib/site/labels";
import { LiveMasteryChip } from "./progress-ui";
import { cardClass, cx, LevelBadge, meta, TitleText, TypeBadge } from "./ui";

const TYPES: (EntryType | "all")[] = ["all", "concept", "troubleshooting", "decision", "pitfall"];

/// 卡片底部的「在哪」：第一条路线第几篇，否则第一个主题
export function whereOf(card: EntryCard) {
  const [route] = card.routes;
  if (route) return `${route.title} · 第 ${route.position} 篇`;
  const [topic] = card.topics;
  return topic ? `主题 · ${topic.title}` : "";
}

/// 类型分段切换（画板 l-seg）
export function TypeTabs({
  value,
  onChange,
}: {
  value: EntryType | "all";
  onChange: (type: EntryType | "all") => void;
}) {
  return (
    <div
      role="tablist"
      aria-label="类型"
      className="inline-flex flex-wrap gap-1 rounded-full bg-line-soft p-1"
    >
      {TYPES.map((t) => (
        <button
          key={t}
          type="button"
          role="tab"
          aria-selected={value === t}
          onClick={() => onChange(t)}
          className={cx(
            "h-9 cursor-pointer rounded-full border-0 px-4 font-medium font-ui text-sm transition-[background-color,color,box-shadow] duration-280 ease-out",
            value === t
              ? "bg-surface text-ink shadow-[0_1px_3px_rgba(31,42,36,0.14)]"
              : "bg-transparent text-ink-3 hover:text-ink",
          )}
        >
          {t === "all" ? "全部" : TYPE_LABELS[t]}
        </button>
      ))}
    </div>
  );
}

export function FilteredEntries({ cards }: { cards: EntryCard[] }) {
  const [type, setType] = useState<EntryType | "all">("all");
  const list = cards.filter((c) => type === "all" || c.type === type);
  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <TypeTabs value={type} onChange={setType} />
        <span className="flex-1" />
        <span className={meta}>按难度从低到高</span>
      </div>
      {list.length > 0 ? (
        <div className="grid grid-cols-2 gap-3.5 max-md:grid-cols-1">
          {list.map((c) => (
            <Link
              key={c.slug}
              href={`/entries/${c.slug}`}
              className={cx(cardClass(true), "flex flex-col gap-2 px-5 py-4.5")}
            >
              <span className="flex flex-wrap items-center gap-2">
                <TypeBadge type={c.type} />
                <LevelBadge difficulty={c.difficulty} />
                <span className="flex-1" />
                <LiveMasteryChip slug={c.slug} />
              </span>
              <span className="font-bold font-display text-[17px] text-ink leading-[1.45]">
                <TitleText text={c.title} />
              </span>
              <span className="font-ui text-ink-3 text-sm leading-[1.6]">{c.summary}</span>
              <span className={meta}>{whereOf(c)}</span>
            </Link>
          ))}
        </div>
      ) : (
        <div className="rounded-card border border-line bg-surface-soft p-7 text-center font-ui text-ink-3 text-ui">
          这个标签下还没有这类条目。
        </div>
      )}
    </>
  );
}
