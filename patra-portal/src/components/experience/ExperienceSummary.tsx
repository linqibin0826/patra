"use client";

import { type ReactNode, useEffect, useRef, useState } from "react";
import {
  formatDays,
  formatPercent,
  formatRange,
  formatWeeksHint,
  summaryScopeLine,
} from "@/lib/experience/format";
import type { Summary, SummaryWindow } from "@/lib/interaction/experience-schema";
import { btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

export type SummaryState =
  | { kind: "loading" }
  | { kind: "unavailable" }
  | { kind: "ok"; summary: Summary };

interface ExperienceSummaryProps {
  state: SummaryState;
  window: SummaryWindow;
  onWindowChange: (window: SummaryWindow) => void;
  onRetry: () => void;
}

/// 写后变化时高亮的几项
type ChangeKey = "count" | "median" | "range" | "desk" | "rate" | "outcomes";

function changedKeys(before: Summary, after: Summary): Set<ChangeKey> {
  const changed = new Set<ChangeKey>();
  const differ = (a: unknown, b: unknown) => JSON.stringify(a) !== JSON.stringify(b);
  if (before.count !== after.count) changed.add("count");
  if (before.firstRound?.median !== after.firstRound?.median) changed.add("median");
  if (
    before.firstRound?.p25 !== after.firstRound?.p25 ||
    before.firstRound?.p75 !== after.firstRound?.p75
  ) {
    changed.add("range");
  }
  if (before.deskReject?.median !== after.deskReject?.median) changed.add("desk");
  if (differ(before.acceptance, after.acceptance)) changed.add("rate");
  if (differ(before.outcomes, after.outcomes)) changed.add("outcomes");
  return changed;
}

/// 写后重取到的新数据里变了的项（简报 §3.13「变化的数字高亮 2 秒」）。只比较同一窗口的前后两份：切窗口
/// 带来的变化不算
function useChanged(summary: Summary | null, window: SummaryWindow): Set<ChangeKey> {
  const previous = useRef<{ window: SummaryWindow; summary: Summary } | null>(null);
  const [changed, setChanged] = useState<Set<ChangeKey>>(() => new Set());
  useEffect(() => {
    if (!summary) {
      return;
    }
    const before = previous.current;
    previous.current = { window, summary };
    if (!before || before.window !== window || before.summary === summary) {
      return;
    }
    const keys = changedKeys(before.summary, summary);
    if (keys.size === 0) {
      return;
    }
    setChanged(keys);
    const timer = setTimeout(() => setChanged(new Set()), 2000);
    return () => clearTimeout(timer);
  }, [summary, window]);
  return changed;
}

/// 变化项的底色：`clay-50` 底，2 秒后淡出；减少动态效果时只换底色
function Flash({ on, children }: { on: boolean; children: ReactNode }) {
  return (
    <span
      data-changed={on ? "" : undefined}
      className={cn(
        "rounded-sm motion-safe:transition-[background-color,box-shadow] motion-safe:duration-700",
        on && "bg-clay-50 ring-4 ring-clay-50",
      )}
    >
      {children}
    </span>
  );
}

const KEY = "font-mono text-2xs tracking-mono text-fg-3";
const BIG =
  "flex items-baseline gap-1.5 font-medium font-serif text-5xl lining-nums tabular-nums leading-none tracking-tight text-ink-900 max-[880px]:text-4xl";
const UNIT = "font-normal font-sans text-fg-2 text-lg tracking-normal";
const BODY =
  "grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] items-stretch gap-x-7 max-[880px]:grid-cols-1 max-[880px]:gap-y-3.5";
const SIDE =
  "flex min-w-0 flex-col justify-center gap-2.5 border-border-subtle border-l pl-7 max-[880px]:border-t max-[880px]:border-l-0 max-[880px]:pt-3.5 max-[880px]:pl-0";
const NOTE = "m-0 mt-auto border-border-subtle border-t pt-3 text-fg-3 text-xs leading-4.5";
const MESSAGE = "m-0 text-fg-2 text-sm leading-5";

/// 期刊页「同行经验」汇总卡（简报 §3.13，画板 `Journal`）：六种状态；统计只用文字与数字，不画图
export function ExperienceSummary({
  state,
  window,
  onWindowChange,
  onRetry,
}: ExperienceSummaryProps) {
  const summary = state.kind === "ok" ? state.summary : null;
  // 卡里的文字与高亮按数据自己的窗口：切窗口时上一份数据还在（等新窗口取回），按钮已经按下了新窗口
  const shown = summary?.window ?? window;
  const changed = useChanged(summary, shown);
  const empty = summary !== null && summary.allCount === 0;
  const showWindows = summary ? !empty : state.kind === "unavailable" && window === "all";
  return (
    <article
      aria-labelledby="experience-summary-title"
      aria-busy={state.kind === "loading" ? true : undefined}
      className="flex min-w-0 flex-col gap-3.5 rounded-lg border border-border-default bg-paper-50 p-5"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3
            id="experience-summary-title"
            className="m-0 font-semibold text-fg-1 text-md leading-5"
          >
            同行经验
          </h3>
          <div className="mt-0.75 font-mono text-3xs text-fg-3 uppercase tracking-mono">
            来源：Patra 用户填写
          </div>
        </div>
        {showWindows && (
          <fieldset className="m-0 flex min-w-0 gap-0.5 rounded-full border-0 bg-bg-sunken p-0.5">
            <legend className="sr-only">统计范围</legend>
            {(["3y", "all"] as const).map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={window === value}
                onClick={() => window !== value && onWindowChange(value)}
                className={cn(
                  "h-6.5 whitespace-nowrap rounded-full px-2.5 font-medium text-xs transition-colors max-[880px]:h-8",
                  window === value
                    ? "bg-bg-elevated text-fg-1 shadow-sm"
                    : "text-fg-2 hover:text-ink-900",
                )}
              >
                {value === "3y" ? "近 3 年" : "全部"}
              </button>
            ))}
          </fieldset>
        )}
      </div>

      {state.kind === "loading" && (
        <div className={cn(BODY, "motion-safe:animate-pulse")} aria-hidden="true">
          <div className="flex flex-col gap-2.5">
            <span className="h-3 w-2/5 rounded bg-paper-200" />
            <span className="h-11 w-1/2 rounded bg-paper-200" />
            <span className="h-3 w-3/4 rounded bg-paper-200" />
          </div>
          <div className={SIDE}>
            <span className="h-3 w-full rounded bg-paper-200" />
            <span className="h-3 w-4/5 rounded bg-paper-200" />
            <span className="h-3 w-3/5 rounded bg-paper-200" />
          </div>
        </div>
      )}

      {state.kind === "unavailable" && (
        <p className={MESSAGE}>
          同行经验暂不可用。
          <button type="button" onClick={onRetry} className={btnText}>
            重试
          </button>
        </p>
      )}

      {summary && empty && <p className={MESSAGE}>还没有同行分享这本期刊的投稿经验。</p>}

      {summary && !empty && summary.count === 0 && (
        <p className={MESSAGE}>
          近 3 年还没有投稿经验。
          <button type="button" onClick={() => onWindowChange("all")} className={btnText}>
            看全部 {summary.allCount} 条
          </button>
        </p>
      )}

      {summary && summary.count > 0 && !summary.sufficient && (
        <>
          <div className={BODY}>
            <div>
              <div className={KEY}>投稿经验</div>
              <div className="mt-2">
                <Flash on={changed.has("count")}>
                  <span className={BIG}>
                    {summary.count}
                    <small className={UNIT}>条</small>
                  </span>
                </Flash>
              </div>
            </div>
            <div className={SIDE}>
              <p className={MESSAGE}>
                {shown === "all" ? "一共有" : "近 3 年有"} {summary.count} 条投稿经验，满 5
                条后显示统计。
              </p>
            </div>
          </div>
          <p className={NOTE}>{shown === "all" ? "全部 · 按投稿日期" : "近 3 年（按投稿日期）"}</p>
        </>
      )}

      {summary && summary.count > 0 && summary.sufficient && (
        <FullSummary summary={summary} window={shown} changed={changed} />
      )}
    </article>
  );
}

function FullSummary({
  summary,
  window,
  changed,
}: {
  summary: Summary;
  window: SummaryWindow;
  changed: Set<ChangeKey>;
}) {
  const { firstRound, deskReject, acceptance, outcomes } = summary;
  const weeks = firstRound ? formatWeeksHint(firstRound.median) : null;
  return (
    <>
      <div className={BODY}>
        <div>
          <div className={KEY}>首轮用时 · 中位数</div>
          <div className="mt-2">
            <Flash on={changed.has("median")}>
              <span className={BIG}>
                {firstRound ? (
                  <>
                    {firstRound.median}
                    <small className={UNIT}>天</small>
                  </>
                ) : (
                  "—"
                )}
              </span>
            </Flash>
          </div>
          {firstRound && (
            <div className="mt-1.5 text-fg-2 text-sm tabular-nums">
              <Flash on={changed.has("range")}>
                {[weeks, `P25–P75 ${formatRange(firstRound.p25, firstRound.p75)}`]
                  .filter(Boolean)
                  .join(" · ")}
              </Flash>
            </div>
          )}
        </div>
        <div className={SIDE}>
          <dl className="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-5 gap-y-2">
            <dt className={cn(KEY, "whitespace-nowrap pt-0.5")}>直接拒稿用时</dt>
            <dd className="m-0 min-w-0 text-fg-1 text-md tabular-nums">
              <Flash on={changed.has("desk")}>
                {deskReject ? formatDays(deskReject.median) : "—"}
              </Flash>
            </dd>
            <dt className={cn(KEY, "whitespace-nowrap pt-0.5")}>录用率</dt>
            <dd className="m-0 min-w-0 text-fg-1 text-md tabular-nums">
              <Flash on={changed.has("rate")}>
                {acceptance ? formatPercent(acceptance.rate) : "—"}
              </Flash>
              {acceptance && outcomes && (
                <small className="mt-0.5 block text-fg-3 text-xs">
                  已完结的 {acceptance.decidedCount} 条：录用 {outcomes.accepted}、拒稿{" "}
                  {outcomes.rejected}
                </small>
              )}
            </dd>
            <dt className={cn(KEY, "whitespace-nowrap pt-0.5")}>结果</dt>
            <dd className="m-0 min-w-0 text-fg-1 text-md tabular-nums">
              <Flash on={changed.has("outcomes")}>
                {outcomes
                  ? `录用 ${outcomes.accepted} · 拒稿 ${outcomes.rejected} · 撤稿 ${outcomes.withdrawn} · 进行中 ${outcomes.inProgress}`
                  : "—"}
              </Flash>
            </dd>
          </dl>
        </div>
      </div>
      <p className={NOTE}>
        <Flash on={changed.has("count")}>{summaryScopeLine(summary.count, window)}</Flash>
      </p>
    </>
  );
}
