"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";

/// 未读数的写法：1–99 写数字，超过写 `99+`
export function formatUnread(count: number): string {
  return count > 99 ? "99+" : String(count);
}

/// 未读数徽标（简报 §3.18）：高 16px、最小宽 16px，`clay-700` 底 + `fg-on-clay` 字，外圈 2px 画布色描边隔开图标。
/// 0 不渲染。挂载后数字每变一次回弹一次（换 key 让动画重放；减弱动效时 `anim-bounce` 不生效），挂载时的数字不弹
export function UnreadBadge({ count, className }: { count: number; className?: string }) {
  const [last, setLast] = useState(count);
  const [bounce, setBounce] = useState(false);
  if (last !== count) {
    setLast(count);
    setBounce(true);
  }
  if (count <= 0) {
    return null;
  }
  return (
    <span
      key={count}
      aria-hidden="true"
      className={cn(
        "inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-clay-700 px-1 font-mono text-3xs leading-none font-medium text-fg-on-clay ring-2 ring-bg-canvas",
        bounce && "anim-bounce",
        className,
      )}
    >
      {formatUnread(count)}
    </span>
  );
}
