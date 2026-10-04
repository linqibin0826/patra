import { FeedSection } from "./index";

const BLOCK = "rounded-sm bg-paper-200 shimmer";

/// 单条文献的骨架：来源行 → 两行标题 → 元信息，与 PaperCard 同构。
function EntrySkeleton({ lead = false }: { lead?: boolean }) {
  return (
    <div
      className={`flex flex-col border-t border-border-default ${lead ? "gap-4 pt-6" : "gap-3 pt-5 pb-7"}`}
    >
      <div className={`h-3 w-40 ${BLOCK}`} />
      <div className={`${lead ? "h-10" : "h-5"} w-11/12 ${BLOCK}`} />
      <div className={`${lead ? "h-10" : "h-5"} w-2/3 ${BLOCK}`} />
      <div className={`h-3 w-1/2 ${BLOCK}`} />
      {lead && <div className={`mt-2 h-24 w-full ${BLOCK}`} />}
    </div>
  );
}

/// ExploreFeed 加载态骨架（Suspense fallback）：外壳与真实区块一致（同一个 FeedSection），避免布局跳动。
export function ExploreFeedSkeleton() {
  return (
    <FeedSection tabs={<div aria-hidden className={`h-11 w-52 rounded-full ${BLOCK}`} />}>
      <div
        data-feed-state="loading"
        aria-hidden="true"
        className="grid grid-cols-1 lg:grid-cols-12 lg:gap-x-12"
      >
        <div className="lg:col-span-7">
          <EntrySkeleton lead />
        </div>
        <div className="lg:col-span-5">
          <EntrySkeleton />
          <EntrySkeleton />
          <EntrySkeleton />
        </div>
      </div>
    </FeedSection>
  );
}
