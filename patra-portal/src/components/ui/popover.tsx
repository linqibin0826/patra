"use client";

import { Dialog as DialogPrimitive } from "@base-ui/react/dialog";
import { Popover } from "@base-ui/react/popover";
import type { ReactNode, RefObject } from "react";
import { useMediaQuery } from "@/lib/use-media-query";
import { cn } from "@/lib/utils";

interface ResponsivePopoverProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /// 浮层锚在它下方，关闭后焦点回到它
  anchor: RefObject<HTMLElement | null>;
  /// 关闭后焦点去哪；不传时回到锚点（锚点不能聚焦时要传，如日期选择锚在外框、焦点回输入框）
  finalFocus?: RefObject<HTMLElement | null>;
  /// 读屏标题（不显示）
  title: string;
  align?: "start" | "end";
  /// 浮层宽度等（桌面）
  className?: string;
  children: ReactNode;
}

/// 浮层原语（简报 §5「原语 popover」）：桌面是锚在触发按钮下方的浮层（`radius-lg`、`shadow-lg`、`paper-50`），
/// ≤ 880px 改为底部抽屉（高度随内容、最高 80vh）。开合由调用方控制：收藏按钮点一下要先收藏再打开。
/// 点外面、`Esc`（抽屉还能点遮罩）关闭，焦点回到锚点，都用 base-ui 自带的。
export function ResponsivePopover({
  open,
  onOpenChange,
  anchor,
  finalFocus = anchor,
  title,
  align = "start",
  className,
  children,
}: ResponsivePopoverProps) {
  const narrow = useMediaQuery("(max-width: 880px)");
  if (narrow) {
    return (
      <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
        <DialogPrimitive.Portal>
          <DialogPrimitive.Backdrop className="fixed inset-0 z-50 bg-bg-scrim transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0" />
          <DialogPrimitive.Popup
            data-variant="sheet"
            finalFocus={finalFocus}
            className="fixed inset-x-0 bottom-0 z-50 flex max-h-[80vh] flex-col overflow-y-auto rounded-t-xl bg-paper-50 px-4 pt-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] text-fg-1 shadow-lg outline-none transition-opacity motion-safe:transition-[opacity,translate] duration-300 ease-out-expo data-ending-style:opacity-0 data-starting-style:opacity-0 motion-safe:data-starting-style:translate-y-8"
          >
            <DialogPrimitive.Title className="sr-only">{title}</DialogPrimitive.Title>
            {children}
          </DialogPrimitive.Popup>
        </DialogPrimitive.Portal>
      </DialogPrimitive.Root>
    );
  }
  return (
    <Popover.Root open={open} onOpenChange={onOpenChange}>
      <Popover.Portal>
        <Popover.Positioner
          anchor={anchor}
          side="bottom"
          align={align}
          sideOffset={8}
          collisionPadding={16}
          className="z-50"
        >
          <Popover.Popup
            data-variant="popover"
            finalFocus={finalFocus}
            className={cn(
              "flex max-h-[min(80vh,560px)] w-80 max-w-[calc(100vw-32px)] flex-col overflow-y-auto rounded-lg border border-border-default bg-paper-50 text-fg-1 shadow-lg outline-none transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0",
              className,
            )}
          >
            <Popover.Title className="sr-only">{title}</Popover.Title>
            {children}
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  );
}
