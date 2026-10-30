import { SETTINGS_MESSAGES as M } from "@/lib/notification/messages";

const BLOCK = "rounded-sm bg-paper-200 shimmer";

/// 设置 › 通知读偏好时的骨架（简报 §3.5「加载中」，画板 `Notify` load › 加载中）：两张卡片，各有标题、说明与
/// 三行单选的占位。读屏只读到一句「正在读取通知设置」。
export function NotificationSettingsSkeleton() {
  return (
    <div role="status" className="flex flex-col gap-8 max-[880px]:gap-6">
      <span className="sr-only">{M.loading}</span>
      {["reply", "mention"].map((key) => (
        <div
          key={key}
          aria-hidden="true"
          className="flex flex-col gap-3.5 rounded-xl border border-border-default bg-bg-paper p-6 max-[880px]:px-4 max-[880px]:py-5"
        >
          <div className={`h-5.5 w-24 ${BLOCK}`} />
          <div className={`h-3.5 w-[52%] ${BLOCK}`} />
          <div className="flex flex-col gap-3 py-1">
            <div className={`h-10 ${BLOCK}`} />
            <div className={`h-10 ${BLOCK}`} />
            <div className={`h-10 ${BLOCK}`} />
          </div>
        </div>
      ))}
    </div>
  );
}
