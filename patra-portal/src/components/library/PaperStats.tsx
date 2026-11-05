"use client";

import Link from "next/link";
import { Fragment, type ReactNode } from "react";
import { useCollectDelta } from "@/components/library/LibraryStatusProvider";
import { formatCount } from "@/lib/format-count";
import type { PaperStats as PaperStatsValue } from "@/lib/interaction/stats";
import { cn } from "@/lib/utils";

interface PaperStatsProps {
  /// 文献 ID
  paperKey: string;
  /// 这一屏取到的收藏人数与讨论数（互动服务）；取不到为 null
  stats: PaperStatsValue | null;
  /// 看过的人数（行为数据服务，v0.18）；取不到为 null，不传等同取不到
  viewerCount?: number | null;
  className?: string;
}

/// 文献卡片的「N 人看过 · N 人收藏 · N 条讨论」（v0.15 简报 §3.12，v0.18 简报 §3.2 加看过）：为 0 的一项不写，都没有时
/// 整行不出现；两个服务各自取不到时只少自己那几项。讨论数链到讨论区；收藏人数是纯文本（不公开收藏人），本人在这一屏
/// 收藏 / 取消时 ±1；看过不即时 +1（60 秒缓存，同一个人只算一次）
export function PaperStats({ paperKey, stats, viewerCount = null, className }: PaperStatsProps) {
  const delta = useCollectDelta(paperKey);
  const collectors = stats ? Math.max(0, stats.collectorCount + delta) : 0;
  const discussions = stats?.discussionCount ?? 0;
  const items: { key: string; node: ReactNode }[] = [];
  if (viewerCount !== null && viewerCount > 0) {
    items.push({ key: "viewers", node: <span>{formatCount(viewerCount)} 人看过</span> });
  }
  if (collectors > 0) {
    items.push({ key: "collectors", node: <span>{formatCount(collectors)} 人收藏</span> });
  }
  if (discussions > 0) {
    items.push({
      key: "discussions",
      node: (
        <Link
          href={`/papers/${paperKey}#discussion`}
          className="underline-offset-3 hover:text-fg-1 hover:underline"
        >
          {formatCount(discussions)} 条讨论
        </Link>
      ),
    });
  }
  if (items.length === 0) {
    return null;
  }
  return (
    <p
      className={cn(
        "flex flex-wrap items-baseline gap-x-2 font-mono text-2xs tabular-nums text-fg-3",
        className,
      )}
    >
      {items.map((item, index) => (
        <Fragment key={item.key}>
          {/* 空格只为复制与读屏时两项分开；flex 里不占位，间距由 gap 给 */}
          {index > 0 && (
            <>
              {" "}
              <span aria-hidden="true" className="text-ink-300">
                ·
              </span>{" "}
            </>
          )}
          {item.node}
        </Fragment>
      ))}
    </p>
  );
}
