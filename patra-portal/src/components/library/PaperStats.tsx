"use client";

import Link from "next/link";
import { useCollectDelta } from "@/components/library/LibraryStatusProvider";
import { formatCount } from "@/lib/format-count";
import type { PaperStats as PaperStatsValue } from "@/lib/interaction/stats";
import { cn } from "@/lib/utils";

interface PaperStatsProps {
  /// 文献 ID
  paperKey: string;
  /// 这一屏取到的计数；取不到为 null
  stats: PaperStatsValue | null;
  className?: string;
}

/// 文献卡片的「N 人收藏 · N 条讨论」（简报 §3.12）：为 0 的一项不写，都为 0 或取不到时整行不出现。讨论数链到
/// 讨论区；收藏人数是纯文本（不公开收藏人），本人在这一屏收藏 / 取消时 ±1
export function PaperStats({ paperKey, stats, className }: PaperStatsProps) {
  const delta = useCollectDelta(paperKey);
  if (stats === null) {
    return null;
  }
  const collectors = Math.max(0, stats.collectorCount + delta);
  const discussions = stats.discussionCount;
  if (collectors === 0 && discussions === 0) {
    return null;
  }
  return (
    <p
      className={cn(
        "flex flex-wrap items-baseline gap-x-2 font-mono text-2xs tabular-nums text-fg-3",
        className,
      )}
    >
      {collectors > 0 && <span>{formatCount(collectors)} 人收藏</span>}
      {/* 空格只为复制与读屏时两项分开；flex 里不占位，间距由 gap 给 */}
      {collectors > 0 && discussions > 0 && (
        <>
          {" "}
          <span aria-hidden="true" className="text-ink-300">
            ·
          </span>{" "}
        </>
      )}
      {discussions > 0 && (
        <Link
          href={`/papers/${paperKey}#discussion`}
          className="underline-offset-3 hover:text-fg-1 hover:underline"
        >
          {formatCount(discussions)} 条讨论
        </Link>
      )}
    </p>
  );
}
