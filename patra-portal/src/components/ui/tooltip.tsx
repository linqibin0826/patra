"use client";

import { Tooltip as TooltipPrimitive } from "@base-ui/react/tooltip";
import type { ReactElement, ReactNode } from "react";
import { cn } from "@/lib/utils";

interface TooltipProps {
  /// 触发元素：base-ui 把它渲染成可聚焦的触发器（悬停与键盘聚焦都能打开）
  trigger: ReactElement;
  /// 说明文字
  children: ReactNode;
  /// 打开前的延迟毫秒数
  delay?: number;
  className?: string;
}

/// 悬停说明（base-ui Tooltip）：悬停或键盘聚焦触发器时出现在上方，墨色底小字。说明只能是补充信息，不放操作
export function Tooltip({ trigger, children, delay = 200, className }: TooltipProps) {
  return (
    <TooltipPrimitive.Provider delay={delay}>
      <TooltipPrimitive.Root>
        <TooltipPrimitive.Trigger render={trigger} />
        <TooltipPrimitive.Portal>
          <TooltipPrimitive.Positioner side="top" sideOffset={6} collisionPadding={16}>
            <TooltipPrimitive.Popup
              role="tooltip"
              className={cn(
                "max-w-72 rounded-md bg-ink-900 px-2.5 py-1.5 text-xs leading-5 text-paper-50 shadow-md transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0",
                className,
              )}
            >
              {children}
            </TooltipPrimitive.Popup>
          </TooltipPrimitive.Positioner>
        </TooltipPrimitive.Portal>
      </TooltipPrimitive.Root>
    </TooltipPrimitive.Provider>
  );
}
