import Link from "next/link";
import type { CSSProperties } from "react";
import { pickCover } from "@/lib/portal-api/cover-palette";
import { cn } from "@/lib/utils";
import type { VenueBrowse } from "@/types/portal";

interface JournalCoverCardProps {
  journal: VenueBrowse;
  className?: string;
}

/// 期刊封面卡：深色学术封面（书脊高光 + 内框压印 + 贴面落影），hover 时抬起、微倾、落影拉长；
/// 下方信息区为全称、缩写、影响因子与 JCR 分区。首页书架与 /journals 网格共用。
export function JournalCoverCard({ journal, className }: JournalCoverCardProps) {
  const { bg, ink } = pickCover(journal.id);
  // 数据驱动的十六进制色无法写成静态 Tailwind 类；按设计系统约定经 CSS 变量传入
  const coverVars = {
    "--cover-bg": bg,
    "--cover-ink": ink,
  } as CSSProperties;

  return (
    <Link
      href={`/journals/${journal.id}`}
      title={journal.name}
      className={cn("group/cover flex flex-col text-inherit no-underline", className)}
    >
      <div
        data-cover
        style={coverVars}
        className="relative aspect-[3/4] overflow-hidden rounded-[3px] bg-(--cover-bg) text-(--cover-ink) shadow-cover transition-[translate,rotate,box-shadow] duration-500 ease-out-expo before:absolute before:inset-y-0 before:left-0 before:w-4 before:bg-(image:--cover-spine) before:content-[''] after:absolute after:inset-y-2.5 after:right-2.5 after:left-5 after:border after:border-current after:opacity-25 after:content-[''] group-hover/cover:shadow-cover-hover motion-safe:group-hover/cover:-translate-y-1.5 motion-safe:group-hover/cover:-rotate-[0.6deg]"
      >
        {journal.foundedYear !== null && (
          <div className="absolute top-4 right-3 left-5 text-center font-mono text-3xs uppercase tracking-spaced opacity-75">
            est. {journal.foundedYear}
          </div>
        )}
        <div className="absolute inset-0 flex items-center justify-center pr-4 pl-6 text-center">
          <span className="font-serif text-[clamp(20px,2vw,26px)] leading-[1.05] font-medium tracking-tight whitespace-pre-line">
            {journal.abbr}
          </span>
        </div>
        <div className="absolute right-3 bottom-4 left-5 text-center font-mono text-3xs tracking-spaced opacity-75">
          vol · 2026
        </div>
      </div>

      <div className="mt-4 flex flex-1 flex-col gap-1">
        <div className="line-clamp-2 min-h-[2.7em] text-sm leading-snug font-semibold text-fg-1 transition-colors duration-200 group-hover/cover:text-clay-700">
          {journal.name}
        </div>
        <div className="truncate font-mono text-2xs uppercase tracking-mono text-fg-3">
          {journal.abbr}
        </div>
        <div className="mt-auto flex items-end justify-between gap-2 border-t border-border-subtle pt-2.5">
          <div className="flex flex-col">
            <span className="font-serif text-2xl leading-none font-medium tracking-tight tabular-nums text-ink-900">
              {journal.impactFactor != null ? journal.impactFactor.toFixed(1) : "—"}
            </span>
            <span className="mt-1.5 font-mono text-3xs uppercase tracking-caps text-fg-3">
              影响因子
            </span>
          </div>
          <div className="text-right">
            <span className="block font-mono text-3xs uppercase tracking-caps text-fg-3">JCR</span>
            <span className="font-mono text-md font-semibold tabular-nums text-ink-900">
              {journal.jcrQuartile ?? "—"}
            </span>
          </div>
        </div>
      </div>
    </Link>
  );
}
