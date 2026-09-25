"use client";

import type { ReactNode } from "react";
import { useBrowseQuery } from "@/components/portal/browse/BrowseQueryProvider";
import { cn } from "@/lib/utils";

/// 结果区包装：导航进行中变淡并标记 aria-busy，新结果（或骨架）到达前给出过渡提示。
export function PendingRegion({ children }: { children: ReactNode }) {
  const { isPending } = useBrowseQuery();
  return (
    <div
      aria-busy={isPending || undefined}
      className={cn("transition-opacity duration-150", isPending && "opacity-60")}
    >
      {children}
    </div>
  );
}
