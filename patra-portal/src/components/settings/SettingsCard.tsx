import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface SettingsCardProps {
  title: string;
  /// 标题下一行说明（`fg-3`）
  description?: ReactNode;
  /// 标题行右侧的附加内容，比如关注领域的计数「3 / 10」
  aside?: ReactNode;
  /// `danger`：页底危险区，描边与标题用危险色（PAP-102 的注销卡片）
  variant?: "default" | "danger";
  /// 锚点，比如账号页的 `#email`
  id?: string;
  children: ReactNode;
}

/// 设置页的分组卡片（简报 §3.5）：衬线标题 + 一行说明 + 内容。卡片之间的间距由外层决定。
export function SettingsCard({
  title,
  description,
  aside,
  variant = "default",
  id,
  children,
}: SettingsCardProps) {
  return (
    <section
      id={id}
      aria-labelledby={id ? `${id}-title` : undefined}
      className={cn(
        "flex flex-col gap-5 rounded-xl border bg-bg-paper p-6 max-[880px]:px-4 max-[880px]:py-5",
        variant === "danger" ? "border-rust-500/40" : "border-border-default",
      )}
    >
      <div>
        <div className="flex items-baseline justify-between gap-4">
          <h2
            id={id ? `${id}-title` : undefined}
            className={cn(
              "font-serif text-xl leading-6.5 font-medium",
              variant === "danger" ? "text-status-danger" : "text-fg-1",
            )}
          >
            {title}
          </h2>
          {aside}
        </div>
        {description && <div className="mt-1.5 text-sm leading-5 text-fg-3">{description}</div>}
      </div>
      {children}
    </section>
  );
}
