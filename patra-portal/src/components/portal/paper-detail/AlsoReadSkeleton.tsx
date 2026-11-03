/// 「看过这篇的人还看了」加载中：标题条 + 3 张卡片骨架（简报 v0.18 §3.6）
export function AlsoReadSkeleton() {
  return (
    <div
      role="status"
      aria-label="正在加载推荐"
      className="mx-auto mt-24 max-w-page border-t border-border-default px-gutter pt-12 max-md:mt-16"
    >
      <div className="mb-6 h-8 w-64 shimmer rounded bg-paper-200" />
      <div className="grid grid-cols-3 gap-x-10 max-lg:grid-cols-2 max-md:grid-cols-1">
        {["a", "b", "c"].map((key) => (
          <div
            key={key}
            aria-hidden
            className="flex flex-col gap-3 border-t border-border-default pt-4"
          >
            <div className="h-3 w-24 shimmer rounded bg-paper-200" />
            <div className="h-5 w-full shimmer rounded bg-paper-200" />
            <div className="h-5 w-3/4 shimmer rounded bg-paper-200" />
            <div className="h-3 w-32 shimmer rounded bg-paper-200" />
          </div>
        ))}
      </div>
    </div>
  );
}
