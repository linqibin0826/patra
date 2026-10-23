"use client";

import { Combobox as ComboboxPrimitive } from "@base-ui/react/combobox";
import { cn } from "@/lib/utils";

// base-ui Combobox 的薄封装，只留选择器用到的部件，颜色、圆角、阴影换成 token。
// 输入框外框与 FormField 的输入井同一套样式；候选在 `ComboboxPopup` 里，宽度跟输入框一样。

const Combobox = ComboboxPrimitive.Root;

/// 输入框（`role="combobox"`、`aria-expanded`、`aria-activedescendant` 由 base-ui 管）。外框由调用方给。
function ComboboxInput({ className, ...props }: ComboboxPrimitive.Input.Props) {
  return (
    <ComboboxPrimitive.Input
      className={cn(
        "h-full min-w-0 flex-1 bg-transparent p-0 text-md leading-6 text-ink-900 outline-none placeholder:text-fg-4 disabled:cursor-not-allowed read-only:text-fg-2 max-[880px]:text-lg",
        className,
      )}
      {...props}
    />
  );
}

/// 候选弹层：在输入框下方 6px，宽度同输入框；`top` 是列表上方的附加内容（加载进度条、提示行）
function ComboboxPopup({
  className,
  children,
  top,
  ...props
}: ComboboxPrimitive.Popup.Props & { top?: React.ReactNode }) {
  return (
    <ComboboxPrimitive.Portal>
      <ComboboxPrimitive.Positioner className="isolate z-50 outline-none" sideOffset={6}>
        <ComboboxPrimitive.Popup
          className={cn(
            "relative max-h-[min(var(--available-height),26rem)] w-(--anchor-width) overflow-y-auto overscroll-contain rounded-lg bg-bg-elevated p-1.5 text-fg-1 shadow-lg outline-none transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0",
            className,
          )}
          {...props}
        >
          {top}
          {children}
        </ComboboxPrimitive.Popup>
      </ComboboxPrimitive.Positioner>
    </ComboboxPrimitive.Portal>
  );
}

const ComboboxList = ComboboxPrimitive.List;

/// 候选项：方向键或悬停高亮时凹陷底色；禁用项（已添加）55% 不透明度
function ComboboxItem({ className, ...props }: ComboboxPrimitive.Item.Props) {
  return (
    <ComboboxPrimitive.Item
      className={cn(
        "flex w-full cursor-pointer items-start gap-3 rounded-md px-2.5 py-2 text-left text-base leading-5 outline-hidden select-none data-highlighted:not-data-disabled:bg-bg-sunken data-disabled:cursor-not-allowed data-disabled:opacity-55",
        className,
      )}
      {...props}
    />
  );
}

/// 读屏播报（找到 N 个、没有结果、搜索出错）与可见的提示行
function ComboboxStatus({ className, ...props }: ComboboxPrimitive.Status.Props) {
  return (
    <ComboboxPrimitive.Status
      className={cn("block px-2.5 text-sm leading-5 text-fg-3 empty:hidden", className)}
      {...props}
    />
  );
}

export { Combobox, ComboboxInput, ComboboxItem, ComboboxList, ComboboxPopup, ComboboxStatus };
