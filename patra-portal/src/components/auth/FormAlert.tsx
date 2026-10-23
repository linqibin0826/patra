import { CircleAlert, CircleCheck, Info, type LucideIcon, TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/// 四种语气（简报 §5）：`danger` 失败（默认）、`success` 成功（如「密码已重设，请用新密码登录」）、`info` 说明
/// （如待确认的改邮箱）、`warning` 警示（如取消注销页的「删除后无法恢复」）
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
  warning: {
    role: "status",
    box: "bg-status-warning-bg",
    icon: TriangleAlert,
    ink: "text-status-warning-ink",
  },
} as const;

interface FormAlertProps {
  tone?: keyof typeof TONES;
  /// 换掉语气默认的图标（待确认改邮箱用信封）
  icon?: LucideIcon;
  /// 右端的操作（「重试」「取消更改」这类文字按钮）；有操作时内容垂直居中
  action?: ReactNode;
  children: ReactNode;
}

/// 提示条：不属于某个字段的结果显示在表单或卡片上方。失败是 `role="alert"`，插入时读屏立即朗读；其余是
/// `role="status"`，不打断正在读的内容。出现时淡入下落，参数和折叠区相同，直接用 `.disclosure-body`
/// （高保真稿 Spec-Form 07）。
export function FormAlert({ tone = "danger", icon, action, children }: FormAlertProps) {
  const { role, box, icon: defaultIcon, ink } = TONES[tone];
  const Icon = icon ?? defaultIcon;
  return (
    <div
      role={role}
      className={cn(
        "disclosure-body flex gap-2.5 rounded-lg px-3.5 py-3 text-base leading-5 text-fg-1",
        action ? "items-center" : "items-start",
        box,
      )}
    >
      <Icon className={cn("size-4 shrink-0", !action && "mt-0.5", ink)} aria-hidden="true" />
      <span className="min-w-0 flex-1 [overflow-wrap:anywhere]">{children}</span>
      {action}
    </div>
  );
}
