// patra-learn/src/components/entry-parts.tsx
// 条目页里读写本地进度的部分：先读这几篇（带掌握度点）、文末掌握度、侧栏路线窗口
"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useProgress } from "@/lib/progress/use-progress";
import { masteryCounts, routeWindow } from "@/lib/site/today";
import type { EntryLink, PrerequisiteItem } from "@/lib/site/views";
import { Mastery } from "@/teaching/mastery";
import { LiveMasteryDot } from "./progress-ui";
import { cx, LevelBadge, meta, panelLabel, pill, TitleText } from "./ui";

export function Prerequisites({
  items,
  indirect,
}: {
  items: PrerequisiteItem[];
  indirect: EntryLink[];
}) {
  const { progress } = useProgress();
  const { read } = masteryCounts(
    items.map((i) => i.slug),
    progress,
  );
  return (
    <section
      aria-label="先读这几篇"
      className="flex flex-col gap-2.5 rounded-card border border-line bg-surface-soft px-4.5 py-4"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <span className={cx(panelLabel, "text-ink-3")}>先读这几篇</span>
        <span className={meta}>
          {read > 0 ? `读过 ${read} / ${items.length} 篇` : "开头那道题答得上，就可以直接往下读"}
        </span>
      </div>
      {items.map((item) => (
        <Link
          key={item.slug}
          href={`/entries/${item.slug}`}
          className="-mx-2.5 flex items-center gap-3 rounded-lg px-2.5 py-2 text-ink hover:bg-surface hover:text-ink"
        >
          <LiveMasteryDot slug={item.slug} />
          <span className="min-w-0 flex-1 font-semibold">
            <TitleText text={item.title} />
          </span>
          <LevelBadge difficulty={item.difficulty} />
        </Link>
      ))}
      {indirect.length > 0 ? (
        <div className="ml-[3px] flex flex-wrap items-baseline gap-2 border-line border-l-2 pl-5">
          <span className={meta}>它又要求先读</span>
          {indirect.map((p) => (
            <Link
              key={p.slug}
              href={`/entries/${p.slug}`}
              className={cx(pill(false, true), "text-[13px]")}
            >
              <LiveMasteryDot slug={p.slug} />
              <TitleText text={p.title} />
            </Link>
          ))}
        </div>
      ) : null}
    </section>
  );
}

/// 毫秒 → 「10/26」
function monthDay(time: number) {
  const date = new Date(time);
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

/// 文末掌握度；打开页面时记下最近阅读时间。Mastery 在挂载时记初始值，所以等进度读到后才挂载
export function EntryMastery({ slug }: { slug: string }) {
  const { progress, ready, setMastery, visit } = useProgress();
  useEffect(() => {
    visit(slug);
  }, [slug, visit]);
  if (!ready) return <div aria-hidden="true" className="min-h-[214px]" />;
  const record = progress[slug];
  return (
    <Mastery
      key={slug}
      value={record?.level ?? 0}
      {...(record?.markedAt ? { lastMarked: monthDay(record.markedAt) } : {})}
      onChange={(level) => setMastery(slug, level)}
    />
  );
}

export function RouteSidebar({
  route,
  current,
}: {
  route: { slug: string; title: string; position: number; total: number; entries: EntryLink[] };
  current: string;
}) {
  const { progress } = useProgress();
  const { start, end } = routeWindow(route.total, route.position);
  const before = route.entries.slice(0, start - 1);
  const shown = route.entries.slice(start - 1, end);
  const after = route.entries.length - end;
  const readBefore = masteryCounts(
    before.map((e) => e.slug),
    progress,
  ).read;
  return (
    <div className="rounded-card border border-line bg-surface p-4.5 shadow-card">
      <div className="mb-1.5 font-bold font-display text-base text-ink">{route.title}</div>
      <div className={cx(meta, "mb-2.5 flex justify-between")}>
        <span>
          第 {route.position} 篇 / {route.total}
        </span>
        <Link href={`/routes/${route.slug}`}>整条路线 →</Link>
      </div>
      {before.length > 0 ? (
        <div className={cx(meta, "pb-2 pl-[30px]")}>
          ↑ 前面 {before.length} 篇 · 读过 {readBefore} 篇
        </div>
      ) : null}
      <ol className="m-0 flex list-none flex-col p-0">
        {shown.map((e, i) => {
          const here = e.slug === current;
          const level = progress[e.slug]?.level ?? 0;
          return (
            <li key={e.slug} className="grid grid-cols-[18px_minmax(0,1fr)] gap-x-3">
              <span className="flex flex-col items-center">
                <i
                  className={cx(
                    "box-border block flex-none rounded-full",
                    here
                      ? "mt-[5px] size-4 border-4 border-teal-mark bg-surface shadow-[0_0_0_4px_var(--color-teal-tint)]"
                      : cx(
                          "mt-[7px] size-3 border-2",
                          level === 0 && "border-ink-5 bg-surface",
                          level === 1 && "border-teal-pale bg-teal-pale",
                          level === 2 && "border-teal-soft bg-teal-soft",
                          level === 3 && "border-teal bg-teal",
                        ),
                  )}
                />
                {i < shown.length - 1 ? (
                  <b className="mt-[3px] min-h-2.5 w-0.5 flex-1 bg-line" />
                ) : null}
              </span>
              <span className="pb-2.5 font-ui text-sm leading-[1.6]">
                {here ? (
                  <span className="font-semibold text-ink">
                    <TitleText text={e.title} />
                  </span>
                ) : (
                  <Link href={`/entries/${e.slug}`} className="text-ink-3 hover:text-teal-deep">
                    <TitleText text={e.title} />
                  </Link>
                )}
              </span>
            </li>
          );
        })}
      </ol>
      {after > 0 ? <div className={cx(meta, "pl-[30px]")}>↓ 后面还有 {after} 篇</div> : null}
    </div>
  );
}
