"use client";

import { Dialog as DialogPrimitive } from "@base-ui/react/dialog";
import type { ReactNode, RefObject } from "react";
import { cn } from "@/lib/utils";

/// 桌面宽度档：sm 400（再认证、离开确认）/ md 480（注销）/ lg 620（头像裁剪，画板 Profile 的宽度）
const WIDTH = {
  sm: "min-[881px]:w-100",
  md: "min-[881px]:w-120",
  lg: "min-[881px]:w-155",
} as const;

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /// 标题下的说明，也是对话框的读屏描述
  description?: ReactNode;
  size?: keyof typeof WIDTH;
  /// false 时 `Esc` 与点遮罩都不关闭（提交中，简报 §6）
  dismissible?: boolean;
  /// 标题下方、正文之前的内容，比如上传失败的提示条
  alert?: ReactNode;
  /// 打开时先聚焦的元素；不传时聚焦第一个可聚焦元素（标签行里有链接时就要指定输入框）
  initialFocus?: RefObject<HTMLElement | null>;
  children: ReactNode;
  className?: string;
}

/// 模态对话框（base-ui Dialog）：桌面居中、圆角卡片；≤ 880px 改为占满屏幕的底部抽屉（简报 §3.7、§6）。
/// 打开时焦点落在第一个可聚焦元素上，关闭后回到触发按钮，都用 base-ui 自带的。
export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  size = "md",
  dismissible = true,
  alert,
  initialFocus,
  children,
  className,
}: DialogProps) {
  return (
    <DialogPrimitive.Root
      open={open}
      disablePointerDismissal={!dismissible}
      onOpenChange={(next) => {
        if (next || dismissible) {
          onOpenChange(next);
        }
      }}
    >
      <DialogPrimitive.Portal>
        <DialogPrimitive.Backdrop className="fixed inset-0 z-50 bg-bg-scrim transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0 supports-backdrop-filter:backdrop-blur-xs" />
        <DialogPrimitive.Popup
          initialFocus={initialFocus}
          className={cn(
            "fixed z-50 flex flex-col gap-5 overflow-y-auto bg-bg-elevated p-6 text-fg-1 shadow-lg outline-none transition-opacity motion-safe:transition-[opacity,translate,scale] duration-300 ease-out-expo",
            "min-[881px]:top-1/2 min-[881px]:left-1/2 min-[881px]:max-h-[calc(100dvh-4rem)] min-[881px]:-translate-x-1/2 min-[881px]:-translate-y-1/2 min-[881px]:rounded-xl data-starting-style:opacity-0 motion-safe:min-[881px]:data-starting-style:scale-[0.98] data-ending-style:opacity-0",
            "max-[880px]:inset-0 max-[880px]:px-4 max-[880px]:pb-[max(1.5rem,env(safe-area-inset-bottom))] motion-safe:max-[880px]:data-starting-style:translate-y-8",
            WIDTH[size],
            className,
          )}
        >
          <div className="flex flex-col gap-1.5">
            <DialogPrimitive.Title className="font-serif text-xl leading-6.5 font-medium text-fg-1">
              {title}
            </DialogPrimitive.Title>
            {description && (
              <DialogPrimitive.Description className="text-sm leading-5 text-fg-2">
                {description}
              </DialogPrimitive.Description>
            )}
          </div>
          {alert}
          {children}
        </DialogPrimitive.Popup>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
