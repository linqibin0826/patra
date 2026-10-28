/// 讨论区加载中（简报 §4.1 `disc.loading`）：编辑器位置一块骨架 + 3 楼（头像圆 + 两行）
export function DiscussionSkeleton() {
  return (
    <div aria-hidden="true" className="flex motion-safe:animate-pulse flex-col gap-5">
      <div className="h-12 rounded-xl bg-paper-200" />
      {[0, 1, 2].map((index) => (
        <div key={index} className="grid grid-cols-[32px_minmax(0,1fr)] gap-3 py-2">
          <span className="size-8 rounded-full bg-paper-200" />
          <span className="flex flex-col gap-2">
            <span className="h-3 w-40 rounded bg-paper-200" />
            <span className="h-3 w-full max-w-120 rounded bg-paper-200" />
          </span>
        </div>
      ))}
    </div>
  );
}
