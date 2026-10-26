import { HydrationBoundary } from "@tanstack/react-query";
import { Suspense } from "react";
import { LibraryListSkeleton } from "@/components/library/LibrarySkeleton";
import { type CollectionContext, LibraryView } from "@/components/library/LibraryView";
import type { LibrarySort } from "@/lib/library/params";
import { prefetchLibrary } from "@/lib/library/server";

interface LibraryPageBodyProps {
  view: string;
  sort: LibrarySort;
  page: number;
  collectionContext?: CollectionContext;
}

/// 文献库内容列（RSC）：条目在自己的 Suspense 里流式到达（决策 H：互动区块独立），换视图 / 排序 / 页时
/// 重新出骨架
export function LibraryPageBody(props: LibraryPageBodyProps) {
  return (
    <Suspense key={`${props.view}-${props.sort}-${props.page}`} fallback={<LibraryListSkeleton />}>
      <Prefetched {...props} />
    </Suspense>
  );
}

async function Prefetched({ view, sort, page, collectionContext }: LibraryPageBodyProps) {
  const state = await prefetchLibrary({ items: { view, sort, page } });
  return (
    <HydrationBoundary state={state}>
      <LibraryView view={view} sort={sort} page={page} collectionContext={collectionContext} />
    </HydrationBoundary>
  );
}
