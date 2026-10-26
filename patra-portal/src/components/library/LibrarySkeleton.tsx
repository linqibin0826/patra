const ROWS = ["a", "b", "c", "d", "e"];
const BLOCK = "rounded-sm bg-paper-200 shimmer";

/// 文献库条目骨架：5 行，`PaperListSkeleton` 的形状去掉摘要行（简报 §3.10）
export function LibraryListSkeleton() {
  return (
    <div aria-hidden="true" className="flex flex-col">
      {ROWS.map((key) => (
        <div key={key} className="flex flex-col gap-2 border-b border-border-subtle py-6">
          <div className={`h-3 w-48 ${BLOCK}`} />
          <div className={`h-5 w-11/12 ${BLOCK}`} />
          <div className={`h-3 w-2/3 ${BLOCK}`} />
          <div className={`h-3 w-1/3 ${BLOCK}`} />
        </div>
      ))}
    </div>
  );
}

/// 侧栏骨架：6 行
export function LibrarySidebarSkeleton() {
  return (
    <div aria-hidden="true" className="flex flex-col gap-2.5 px-3.5 py-2">
      {["a", "b", "c", "d", "e", "f"].map((key) => (
        <div key={key} className={`h-4 w-4/5 ${BLOCK}`} />
      ))}
    </div>
  );
}
