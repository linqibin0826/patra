// patra-learn/src/components/route-parts.tsx
// 路线页里读本地进度的部分（画板 Route）：进度卡与「接着读」、按难度分段的篇目行
"use client";

import Link from "next/link";
import { useProgress } from "@/lib/progress/use-progress";
import { DIFFICULTY_AUDIENCE } from "@/lib/site/labels";
import { masteryCounts, nextUnread } from "@/lib/site/today";
import type { RouteRow } from "@/lib/site/views";
import { ProgressBar } from "./progress-ui";
import { button, cardClass, cx, LevelBadge, MasteryChip, meta, TitleText, TypeBadge } from "./ui";

interface Band {
  difficulty: RouteRow["card"]["difficulty"];
  rows: RouteRow[];
}

function useRouteProgress(bands: Band[]) {
  const { progress, ready } = useProgress();
  const slugs = bands.flatMap((b) => b.rows.map((r) => r.card.slug));
  const started = ready && masteryCounts(slugs, progress).read > 0;
  return { progress, slugs, started, next: started ? nextUnread(slugs, progress) : undefined };
}

export function RouteProgressCard({ bands }: { bands: Band[] }) {
  const { progress, slugs, started, next } = useRouteProgress(bands);
  const rows = bands.flatMap((b) => b.rows);
  const [first] = rows;
  const target = rows.find((r) => r.card.slug === next);
  if (!started) {
    return (
      <div className={cx(cardClass(), "flex flex-[1_1_300px] flex-col gap-2.5 px-5.5 py-5")}>
        <span className="font-ui text-ink-3 text-ui">
          {first
            ? `${first.card.difficulty === "intro" ? "第一篇不需要任何基础" : `第一篇写给${DIFFICULTY_AUDIENCE[first.card.difficulty]}的人`}，约 ${first.card.minutes} 分钟。`
            : "这条路线还没有篇目。"}
        </span>
        {first ? (
          <Link
            href={`/entries/${first.card.slug}`}
            className={cx(button("primary"), "self-start")}
          >
            从第一篇开始
          </Link>
        ) : null}
      </div>
    );
  }
  return (
    <div className={cx(cardClass(), "flex flex-[1_1_300px] flex-col gap-3 px-5.5 py-5")}>
      <div className="flex items-baseline gap-2">
        <span className="font-display font-semibold text-[36px] text-ink tabular-nums leading-none">
          {masteryCounts(slugs, progress).read}
        </span>
        <span className="font-ui text-ink-3 text-ui">/ {slugs.length} 篇读过</span>
      </div>
      <ProgressBar slugs={slugs} height="h-2" />
      {target ? (
        <Link href={`/entries/${target.card.slug}`} className={cx(button("primary"), "self-start")}>
          接着读第 {target.n} 篇
        </Link>
      ) : (
        <span className="font-ui text-ink-3 text-ui">这条路线每一篇都标过了。</span>
      )}
    </div>
  );
}

export function RouteBands({ bands }: { bands: Band[] }) {
  const { progress, started, next } = useRouteProgress(bands);
  const firstSlug = bands[0]?.rows[0]?.card.slug;
  return (
    <section className="flex max-w-[880px] flex-col gap-7">
      {bands.map((band) => (
        <div
          key={band.difficulty}
          className="grid grid-cols-[150px_minmax(0,1fr)] gap-6 max-sm:grid-cols-1 max-sm:gap-2"
        >
          <div className="flex flex-col gap-1 pt-2.5">
            <LevelBadge difficulty={band.difficulty} />
            <span className={meta}>{DIFFICULTY_AUDIENCE[band.difficulty]}</span>
          </div>
          <ol className="m-0 flex list-none flex-col p-0">
            {band.rows.map((row) => {
              const level = progress[row.card.slug]?.level ?? 0;
              const isNext = started ? row.card.slug === next : row.card.slug === firstSlug;
              const note = isNext
                ? started
                  ? "接着读这一篇"
                  : "从这里开始"
                : row.alsoIn.length > 0
                  ? `也在 ${row.alsoIn.join("、")}`
                  : "";
              return (
                <li key={row.card.slug}>
                  <Link
                    href={`/entries/${row.card.slug}`}
                    aria-current={isNext ? "step" : undefined}
                    className={cx(
                      "mb-2 flex items-center gap-3.5 rounded-xl border px-3.5 py-2.5 text-ink transition-[background-color,border-color] duration-160 ease-out max-sm:flex-wrap max-sm:gap-2",
                      isNext
                        ? "border-teal-mark bg-surface shadow-[0_0_0_4px_var(--color-teal-tint)]"
                        : "border-transparent hover:border-line hover:bg-surface",
                    )}
                  >
                    <span className="w-[22px] font-display text-ink-4 text-sm tabular-nums">
                      {row.n}
                    </span>
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="font-bold font-display text-[16.5px] text-ink">
                        <TitleText text={row.card.title} />
                      </span>
                      {note ? (
                        <span className={cx(meta, isNext && "font-semibold text-teal")}>
                          {note}
                        </span>
                      ) : null}
                    </span>
                    <TypeBadge type={row.card.type} />
                    <span className={cx(meta, "w-[52px] text-right")}>{row.card.minutes} 分钟</span>
                    <span className="flex w-[72px] justify-center">
                      <MasteryChip level={level} />
                    </span>
                  </Link>
                </li>
              );
            })}
          </ol>
        </div>
      ))}
    </section>
  );
}
