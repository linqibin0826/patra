"use client";

import { useEffect, useMemo, useState } from "react";
import { useActiveHeading } from "@/lib/use-active-heading";
import { useReadingProgress } from "@/lib/use-reading-progress";

export interface CrumbPosition {
  /** 章节标题或文末区块的 DOM id */
  id: string;
  /** 面包屑里显示的位置：章节号（`4.5`）或区块名（「参考文献」） */
  label: string;
}

/**
 * 面包屑中段（spec §5.16）：页头滚出视口后出现短标题；有可读全文时右边再加「位置 · 已读 p%」，
 * 位置是当前章节号或文末区块名，p 是整篇进度。窄屏（≤ 720px）不显示短标题。
 */
export function CrumbStatus({
  shortTitle,
  positions,
}: {
  shortTitle: string;
  positions: CrumbPosition[];
}) {
  const [pastMasthead, setPastMasthead] = useState(false);
  const ids = useMemo(() => positions.map((p) => p.id), [positions]);
  const activeId = useActiveHeading(ids);
  const progress = useReadingProgress();

  useEffect(() => {
    const masthead = document.getElementById("masthead");
    if (!masthead) return;
    const observer = new IntersectionObserver(([entry]) => {
      if (entry) setPastMasthead(!entry.isIntersecting);
    });
    observer.observe(masthead);
    return () => observer.disconnect();
  }, []);

  if (!pastMasthead) {
    return null;
  }
  const position = positions.find((p) => p.id === activeId)?.label;
  const percent = `已读 ${Math.round(progress.whole * 100)}%`;

  return (
    <div className="flex min-w-0 flex-1 items-center justify-between gap-6">
      <span className="min-w-0 truncate font-sans text-sm tracking-normal text-fg-2 max-[720px]:hidden">
        {shortTitle}
      </span>
      {positions.length > 0 && (
        <span className="shrink-0 tabular-nums">
          {progress.started && position ? `${position} · ${percent}` : percent}
        </span>
      )}
    </div>
  );
}
