import Link from "next/link";
import type { CSSProperties } from "react";
import { pickCover } from "@/lib/portal-api/cover-palette";
import type { VenueBrowse } from "@/types/portal";

interface JournalCoverCardProps {
  journal: VenueBrowse;
  className?: string;
}

export function JournalCoverCard({ journal, className }: JournalCoverCardProps) {
  const { bg, ink } = pickCover(journal.id);
  // data-driven hex colors cannot be expressed as static Tailwind utilities;
  // CSS variables are the canonical escape hatch per design system.
  const coverVars = {
    "--cover-bg": bg,
    "--cover-ink": ink,
  } as CSSProperties;

  return (
    <Link
      href={`/journals/${journal.id}`}
      title={journal.name}
      className={
        "flex flex-col overflow-hidden rounded-lg border border-border-default bg-paper-50 text-inherit no-underline transition hover:-translate-y-px hover:border-ink-300 hover:shadow-md " +
        (className ?? "")
      }
    >
      <div
        data-cover
        style={coverVars}
        className="relative flex aspect-[3/4] items-center justify-center overflow-hidden bg-(--cover-bg) p-4 text-center text-(--cover-ink) before:absolute before:inset-2 before:border before:border-current before:opacity-20 before:content-['']"
      >
        {journal.foundedYear !== null && (
          <div className="absolute left-2 right-2 top-3 text-center font-mono text-3xs uppercase tracking-spaced opacity-70">
            est. {journal.foundedYear}
          </div>
        )}
        <div className="whitespace-pre-line font-serif font-medium leading-[1.05] tracking-tight text-[clamp(20px,2.2vw,26px)]">
          {journal.abbr}
        </div>
        <div className="absolute bottom-3 left-2 right-2 text-center font-mono text-3xs tracking-spaced opacity-60">
          vol · 2026
        </div>
      </div>
      <div className="flex flex-col gap-1.5 border-t border-border-default bg-paper-50 p-3.5">
        <div className="line-clamp-2 min-h-[2.6rem] text-sm font-semibold leading-snug text-fg-1">
          {journal.name}
        </div>
        <div className="font-mono text-2xs uppercase tracking-caps text-fg-3">{journal.abbr}</div>
        <div className="mt-1 flex items-baseline justify-between gap-2">
          <div className="flex flex-col">
            <span className="font-serif text-xl font-medium leading-tight tracking-tight text-ink-900 tabular-nums">
              {journal.impactFactor != null ? journal.impactFactor.toFixed(1) : "—"}
            </span>
            <span className="font-mono text-3xs uppercase tracking-caps text-fg-3">影响因子</span>
          </div>
          <div className="text-right font-mono leading-snug text-fg-2">
            <span className="block font-mono text-3xs uppercase tracking-caps text-fg-3">
              JCR 分区
            </span>
            <span className="text-base font-semibold tabular-nums">
              {journal.jcrQuartile ?? "—"}
            </span>
          </div>
        </div>
      </div>
    </Link>
  );
}
