import { CircleAlert, CircleCheck, Info } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/// 三种语气（简报 §5）：`danger` 失败（默认）、`success` 成功（如「密码已重设，请用新密码登录」）、`info` 说明
const TONES = {
  danger: {
    role: "alert",
    box: "bg-status-danger-bg",
    icon: CircleAlert,
    ink: "text-status-danger",
  },
  success: {
    role: "status",
    box: "bg-status-success-bg",
    icon: CircleCheck,
    ink: "text-status-success-ink",
  },
  info: { role: "status", box: "bg-status-info-bg", icon: Info, ink: "text-status-info" },
} as const;

/// 表单级提示条：不属于某个字段的结果显示在表单上方。失败是 `role="alert"`，插入时读屏立即朗读；成功与说明
/// 是 `role="status"`，不打断正在读的内容。出现时淡入下落，参数和折叠区相同，直接用 `.disclosure-body`
/// （高保真稿 Spec-Form 07）。
export function FormAlert({
  tone = "danger",
  children,
}: {
  tone?: keyof typeof TONES;
  children: ReactNode;
}) {
  const { role, box, icon: Icon, ink } = TONES[tone];
  return (
    <div
      role={role}
      className={cn(
        "disclosure-body flex items-start gap-2.5 rounded-lg px-3.5 py-3 text-base leading-5 text-fg-1",
        box,
      )}
    >
      <Icon className={cn("mt-0.5 size-4 shrink-0", ink)} aria-hidden="true" />
      <span className="min-w-0">{children}</span>
    </div>
  );
}
