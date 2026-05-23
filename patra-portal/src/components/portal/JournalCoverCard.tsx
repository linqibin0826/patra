import type { CSSProperties } from "react";
import type { Journal } from "@/types/portal";

interface JournalCoverCardProps {
  journal: Journal;
  className?: string;
}

export function JournalCoverCard({ journal, className }: JournalCoverCardProps) {
  const { bg, ink } = journal.coverStyle;
  // data-driven hex colors cannot be expressed as static Tailwind utilities;
  // CSS variables are the canonical escape hatch per design system.
  const coverVars = {
    "--cover-bg": bg,
    "--cover-ink": ink,
  } as CSSProperties;

  return (
    <button
      type="button"
      disabled
      aria-disabled="true"
      title={`${journal.name} · 功能即将上线`}
      className={
        "flex flex-col overflow-hidden rounded-lg border border-border-default bg-paper-50 text-inherit no-underline transition hover:-translate-y-px hover:border-ink-300 hover:shadow-[0_6px_16px_-10px_rgba(28,25,23,0.18)] disabled:cursor-not-allowed " +
        (className ?? "")
      }
    >
      <div
        data-cover
        style={coverVars}
        className="relative flex aspect-[3/4] items-center justify-center overflow-hidden bg-[var(--cover-bg)] p-4 text-center text-[var(--cover-ink)] before:absolute before:inset-2 before:border before:border-current before:opacity-20 before:content-['']"
      >
        <div className="absolute left-2 right-2 top-3 text-center font-mono text-[9.5px] uppercase tracking-[0.18em] opacity-70">
          est. {journal.founded}
        </div>
        <div className="whitespace-pre-line font-serif font-medium leading-[1.05] tracking-tight text-[clamp(20px,2.2vw,26px)]">
          {journal.cover}
        </div>
        <div className="absolute bottom-3 left-2 right-2 text-center font-mono text-[9px] uppercase tracking-[0.14em] opacity-60">
          vol · 2026
        </div>
      </div>
      <div className="flex flex-col gap-1.5 border-t border-border-default bg-paper-50 p-3.5">
        <div className="text-sm font-semibold leading-snug text-fg-1">{journal.name}</div>
        <div className="font-mono text-[10.5px] uppercase tracking-caps text-fg-3">
          {journal.abbr}
        </div>
        <div className="mt-1 flex items-baseline justify-between gap-2">
          <div className="flex flex-col">
            <span className="font-serif text-xl font-medium leading-tight tracking-tight text-ink-900 tabular-nums">
              {journal.impact.toFixed(1)}
            </span>
            <span className="font-mono text-[9.5px] uppercase tracking-caps text-fg-3">
              影响因子
            </span>
          </div>
          <div className="text-right font-mono text-[11px] leading-snug tabular-nums text-fg-2">
            <span className="block font-mono text-[9.5px] uppercase tracking-caps text-fg-3">
              本周收录
            </span>
            {journal.weekly}
          </div>
        </div>
      </div>
    </button>
  );
}
