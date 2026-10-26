"use client";

import { Check } from "lucide-react";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { useCopy } from "@/lib/use-copy";
import { cn } from "@/lib/utils";

interface CopyButtonProps {
  /// 按钮文字，如「复制链接」「复制 12 条引用」
  label: string;
  text: string;
  /// 降级时要选中的元素（引用预览框、链接输入框）
  target: () => HTMLElement | null;
  disabled?: boolean;
  /// 主按钮（引用对话框）还是次按钮（链接框、分享面板）
  primary?: boolean;
  className?: string;
}

/// 复制按钮：成功时按钮内 `Check` + `已复制` 1.6 秒，不出 toast；都失败时按钮位置换成快捷键提示（简报 §3.8）
export function CopyButton({ label, text, target, disabled, primary, className }: CopyButtonProps) {
  const { state, hint, copy } = useCopy();
  if (state === "selected") {
    return (
      <span aria-live="polite" className={cn("text-sm text-fg-2", className)}>
        {hint}
      </span>
    );
  }
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => void copy(text, target())}
      className={cn(
        primary ? btnPrimary : btnSecondary,
        "px-4 py-2 text-sm",
        state === "copied" && "text-status-success-ink",
        className,
      )}
    >
      {state === "copied" ? (
        <>
          <Check className="size-4 anim-pop" aria-hidden="true" />
          已复制
        </>
      ) : (
        label
      )}
    </button>
  );
}
