import { CircleAlert } from "lucide-react";
import type { ReactNode } from "react";

/// 表单级提示条：失败原因不属于某个字段时显示在表单上方。`role="alert"`，插入时读屏立即朗读。
/// 出现时淡入下落，参数和折叠区相同，直接用 `.disclosure-body`（高保真稿 Spec-Form 07）。
export function FormAlert({ children }: { children: ReactNode }) {
  return (
    <div
      role="alert"
      className="disclosure-body flex items-start gap-2.5 rounded-lg bg-status-danger-bg px-3.5 py-3 text-base leading-5 text-fg-1"
    >
      <CircleAlert className="mt-0.5 size-4 shrink-0 text-status-danger" aria-hidden="true" />
      <span className="min-w-0">{children}</span>
    </div>
  );
}
