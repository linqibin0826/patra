const ROWS = ["a", "b", "c", "d", "e"];

/** 文献列表骨架（Suspense fallback）：5 条与 PaperListItem 同构的占位，避免布局跳动。 */
export function PaperListSkeleton() {
  return (
    <div aria-hidden="true" className="flex flex-col">
      {ROWS.map((key) => (
        <div key={key} className="flex flex-col gap-2 border-b border-border-subtle py-4">
          <div className="h-3 w-40 animate-pulse rounded bg-paper-200" />
          <div className="h-5 w-11/12 animate-pulse rounded bg-paper-200" />
          <div className="h-3 w-2/3 animate-pulse rounded bg-paper-200" />
          <div className="h-3 w-full animate-pulse rounded bg-paper-200" />
        </div>
      ))}
    </div>
  );
}
