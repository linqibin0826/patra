import { Check } from "lucide-react";

/// 即时保存的一组设置所处的状态
export type SaveState = "saving" | "saved" | "failed";

/// 卡片标题右侧的保存状态（读屏礼貌播报）：保存中… / ✓ 已保存 / 没有保存成功，请重试（隐私、通知两页共用）
export function SaveStatus({ status }: { status: SaveState | undefined }) {
  return (
    <span aria-live="polite" className="text-sm leading-5">
      {status === "saving" && <span className="text-fg-3">保存中…</span>}
      {status === "saved" && (
        <span className="inline-flex items-center gap-1.5 text-status-success-ink">
          <Check className="size-3.5" strokeWidth={2} aria-hidden="true" />
          已保存
        </span>
      )}
      {status === "failed" && <span className="text-status-danger">没有保存成功，请重试</span>}
    </span>
  );
}
