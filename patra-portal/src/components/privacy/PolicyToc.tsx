"use client";

import { useActiveHeading } from "@/lib/use-active-heading";
import { cn } from "@/lib/utils";

/// 隐私政策的目录（简报 v0.18 §3.9）：桌面吸顶在左侧，当前节标一道陶土短竖线；窄屏移到正文上方、不吸顶
export function PolicyToc({ items }: { items: readonly { id: string; label: string }[] }) {
  const active = useActiveHeading(items.map((item) => item.id)) ?? items[0]?.id;
  return (
    <nav
      aria-label="本页目录"
      className="sticky top-22 flex flex-col gap-0.5 border-l border-border-default pl-3.5 max-md:static"
    >
      <b className="mb-2.5 font-mono text-3xs font-medium tracking-caps text-fg-3 uppercase">
        目录
      </b>
      {items.map((item) => (
        <a
          key={item.id}
          href={`#${item.id}`}
          aria-current={item.id === active ? "location" : undefined}
          className={cn(
            "relative py-1.25 text-sm text-fg-2 hover:text-fg-1",
            // 当前节：压在目录左边线上的一道陶土短竖线
            item.id === active &&
              "font-medium text-fg-1 before:absolute before:top-2 before:bottom-2 before:-left-3.75 before:w-0.5 before:rounded-full before:bg-clay-500",
          )}
        >
          {item.label}
        </a>
      ))}
    </nav>
  );
}
