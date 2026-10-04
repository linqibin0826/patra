const ROWS = ["a", "b", "c", "d", "e"];

const BLOCK = "rounded-sm bg-paper-200 shimmer";

/** 文献列表骨架（Suspense fallback）：5 条与 PaperListItem 同构的占位（纸面微光），避免布局跳动。 */
export function PaperListSkeleton() {
  return (
    <div aria-hidden="true" className="flex flex-col">
      {ROWS.map((key) => (
        <div key={key} className="flex flex-col gap-2.5 border-b border-border-subtle py-6">
          <div className={`h-3 w-48 ${BLOCK}`} />
          <div className={`h-5 w-11/12 ${BLOCK}`} />
          <div className={`h-3 w-2/3 ${BLOCK}`} />
          <div className={`mt-1 h-3.5 w-full ${BLOCK}`} />
          <div className={`h-3.5 w-4/5 ${BLOCK}`} />
        </div>
      ))}
    </div>
  );
}
