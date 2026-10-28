/// 提醒列表加载中（简报 §3.19）：6 行骨架（圆点列 + 头像圆 + 三行）
export function NotificationSkeleton() {
  return (
    <div aria-hidden="true" className="flex motion-safe:animate-pulse flex-col">
      {[0, 1, 2, 3, 4, 5].map((index) => (
        <div
          key={index}
          data-testid="notification-skeleton-row"
          className="grid min-h-18 grid-cols-[8px_36px_minmax(0,1fr)] gap-x-3 border-b border-border-subtle px-3 py-4"
        >
          <span />
          <span className="size-9 rounded-full bg-paper-200" />
          <span className="flex flex-col gap-2">
            <span className="h-3 w-48 rounded bg-paper-200" />
            <span className="h-3 w-full max-w-120 rounded bg-paper-200" />
            <span className="h-2.5 w-64 rounded bg-paper-200" />
          </span>
        </div>
      ))}
    </div>
  );
}
