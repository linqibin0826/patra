"use client";

import { TrendingUp } from "lucide-react";
import { useId } from "react";
import { Tooltip } from "@/components/ui/tooltip";

const TIP = "近 14 天读的人多，在热读榜前 100 名。只是标记，不影响检索排序。";

/// 检索结果来源行右端的「近期热读」（简报 v0.18 §3.4）：在热读榜前 100 名的文献才有。悬停或键盘聚焦出说明；只是
/// 标记，不影响排序
export function HotBadge() {
  const descriptionId = useId();
  return (
    <Tooltip
      trigger={
        <button
          type="button"
          aria-describedby={descriptionId}
          className="inline-flex shrink-0 cursor-help items-center gap-1 font-mono text-2xs font-medium text-clay-700"
        >
          <TrendingUp className="size-3" strokeWidth={2} aria-hidden="true" />
          近期热读
          {/* base-ui 的 Tooltip 不给触发器加描述，读屏用户靠这一段读到说明 */}
          <span id={descriptionId} className="sr-only">
            {TIP}
          </span>
        </button>
      }
    >
      {TIP}
    </Tooltip>
  );
}
