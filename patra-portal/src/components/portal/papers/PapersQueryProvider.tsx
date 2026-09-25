"use client";

import type { ReactNode } from "react";
import { BrowseQueryProvider } from "@/components/portal/browse/BrowseQueryProvider";
import { serializePaperSearchQuery } from "@/lib/portal-api/paper-search";
import type { PaperSearchQuery } from "@/types/portal";

/// 文献检索页查询 Provider：注入文献页序列化函数与 /papers 路径（函数不能从 RSC 传给 client，故包一层）。
export function PapersQueryProvider({
  query,
  children,
}: {
  query: PaperSearchQuery;
  children: ReactNode;
}) {
  return (
    <BrowseQueryProvider query={query} basePath="/papers" serialize={serializePaperSearchQuery}>
      {children}
    </BrowseQueryProvider>
  );
}
