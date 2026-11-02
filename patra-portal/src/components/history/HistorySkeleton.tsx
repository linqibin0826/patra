/// 阅读历史加载中（简报 v0.18 §3.7 第 9 条）：组标题一条 + 5 行骨架
export function HistorySkeleton() {
  return (
    <div aria-label="加载中" role="status">
      <div className="mt-5 border-b border-border-subtle pb-2">
        <div className="h-3 w-30 shimmer rounded bg-paper-200" />
      </div>
      {Array.from({ length: 5 }, (_, i) => (
        <div
          // biome-ignore lint/suspicious/noArrayIndexKey: 固定 5 行的骨架，没有身份
          key={i}
          aria-hidden="true"
          className="grid grid-cols-[56px_minmax(0,1fr)_32px] gap-4 border-b border-border-subtle py-4.5"
        >
          <div className="mt-1.5 h-3 w-10 shimmer rounded bg-paper-200" />
          <div className="flex flex-col gap-2">
            <div className="h-4.5 w-[86%] shimmer rounded bg-paper-200" />
            <div className="h-3 w-[30%] shimmer rounded bg-paper-200" />
          </div>
          <span />
        </div>
      ))}
    </div>
  );
}
