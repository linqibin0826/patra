import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
/// ExploreFeed 加载态骨架（Suspense fallback）。结构与真实 FeedSection 容器一致，避免布局跳动。
export function ExploreFeedSkeleton() {
  return (
    <section
      data-section="explore-feed"
      data-feed-state="loading"
      className="container mx-auto max-w-[1200px] px-6 py-14 max-[880px]:py-10"
    >
      <div className="mb-6">
        <SectionEyebrow className="mb-0">文献流</SectionEyebrow>
        <h2 className="mt-1 font-serif text-3xl font-medium leading-tight tracking-tight text-ink-900">
          值得读一读的文献
        </h2>
        <p className="mt-1 text-sm text-fg-3">
          每篇都带一段 AI 速读 —— 由 Patra 在采集时生成，仅作为线索，不能替代阅读原文。
        </p>
      </div>
      <div className="grid grid-cols-2 gap-5 max-[880px]:grid-cols-1" aria-hidden="true">
        {["a", "b", "c", "d"].map((k) => (
          <div
            key={k}
            className="h-48 animate-pulse rounded-lg border border-border-default bg-paper-100"
          />
        ))}
      </div>
    </section>
  );
}
