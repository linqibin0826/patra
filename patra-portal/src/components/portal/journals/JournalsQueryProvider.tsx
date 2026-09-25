"use client";

import type { ReactNode } from "react";
import { BrowseQueryProvider } from "@/components/portal/browse/BrowseQueryProvider";
import { serializeVenueBrowseQuery } from "@/lib/portal-api/venue-browse";
import type { VenueBrowseQuery } from "@/types/portal";

/// 期刊浏览页查询 Provider：注入期刊页序列化函数与 /journals 路径（函数不能从 RSC 传给 client，故包一层）。
export function JournalsQueryProvider({
  query,
  children,
}: {
  query: VenueBrowseQuery;
  children: ReactNode;
}) {
  return (
    <BrowseQueryProvider query={query} basePath="/journals" serialize={serializeVenueBrowseQuery}>
      {children}
    </BrowseQueryProvider>
  );
}
