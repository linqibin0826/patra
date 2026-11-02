"use client";

import { Select as SelectPrimitive } from "@base-ui/react/select";
import { Check, ChevronDown } from "lucide-react";
import { useMediaQuery } from "@/lib/use-media-query";
import { cn } from "@/lib/utils";

// shadcn base 版 select 的改写（画布「表单控件重设计」）：弹层在触发器下方、不盖住触发器，
// 颜色、圆角、阴影、动效与 combobox / dropdown-menu 一致；窄屏（≤ 880px）给了 `sheetTitle` 时改底部抽屉。

const Select = SelectPrimitive.Root;

/// 表单触发器的外框与门户 `FIELD_BOX` 同一套：内凹描边、悬停加深、聚焦或展开时陶土描边加光晕、出错变红、禁用凹陷
const FIELD_TRIGGER =
  "group/select flex h-11 w-full min-w-0 cursor-pointer items-center gap-2 rounded-lg border border-border-strong bg-bg-elevated pr-3 pl-4 text-left text-md leading-6 text-ink-900 shadow-inset outline-none ring-clay-500/15 transition-[border-color,box-shadow,background-color] duration-200 hover:border-ink-500 focus-visible:border-border-focus data-popup-open:border-border-focus data-popup-open:ring-4 aria-invalid:border-status-danger aria-invalid:ring-rust-500/15 data-disabled:cursor-not-allowed data-disabled:border-border-default data-disabled:bg-bg-sunken data-disabled:text-fg-2 data-disabled:shadow-none max-[880px]:text-lg";

/// 紧凑触发器（工具条）：32 高、radius-md、paper-50 底
const COMPACT_TRIGGER =
  "group/select inline-flex h-8 cursor-pointer items-center gap-1 rounded-md border border-border-default bg-paper-50 pr-2 pl-3 font-medium text-fg-1 text-sm outline-none ring-clay-500/15 transition-[border-color,box-shadow] duration-150 hover:border-border-hover focus-visible:border-border-focus data-popup-open:border-border-focus data-popup-open:ring-3 max-[880px]:h-10";

/// 触发器：`field` 是表单里的输入框外形（`md` 44 高 / `lg` 48 高），`compact` 是工具条里的小按钮。右端箭头展开时转半圈
function SelectTrigger({
  variant = "field",
  size = "md",
  className,
  children,
  ...props
}: SelectPrimitive.Trigger.Props & { variant?: "field" | "compact"; size?: "md" | "lg" }) {
  return (
    <SelectPrimitive.Trigger
      data-slot="select-trigger"
      className={cn(
        variant === "field" ? FIELD_TRIGGER : COMPACT_TRIGGER,
        variant === "field" && size === "lg" && "h-12 rounded-xl",
        className,
      )}
      {...props}
    >
      {children}
      <SelectPrimitive.Icon
        className={cn(
          "pointer-events-none shrink-0 text-fg-3 motion-safe:transition-transform motion-safe:duration-300 ease-out-expo group-data-popup-open/select:rotate-180",
          variant === "field" ? "ml-auto size-4" : "size-3.5",
        )}
        render={<ChevronDown aria-hidden="true" />}
      />
    </SelectPrimitive.Trigger>
  );
}

/// 当前值的文字；没选时显示占位（`fg-4`），太长截断
function SelectValue({ className, ...props }: SelectPrimitive.Value.Props) {
  return (
    <SelectPrimitive.Value
      data-slot="select-value"
      className={cn("min-w-0 truncate data-placeholder:text-fg-4", className)}
      {...props}
    />
  );
}

const POPUP =
  "max-h-[min(var(--available-height),19rem)] w-max min-w-[max(var(--anchor-width),11.5rem)] max-w-[calc(100vw-2rem)] origin-(--transform-origin) overflow-y-auto overscroll-contain rounded-lg bg-bg-elevated p-1.5 text-fg-1 shadow-lg outline-none transition-opacity motion-safe:transition-[opacity,scale,translate] duration-300 ease-out-expo data-starting-style:opacity-0 motion-safe:data-starting-style:-translate-y-2 motion-safe:data-starting-style:scale-[0.98] data-ending-style:opacity-0";

const SHEET =
  "max-h-[80vh] w-full overflow-y-auto overscroll-contain rounded-t-xl bg-paper-50 px-4 pt-3 pb-[max(1.75rem,env(safe-area-inset-bottom))] text-fg-1 shadow-lg outline-none transition-opacity motion-safe:transition-[opacity,translate] duration-300 ease-out-expo data-starting-style:opacity-0 motion-safe:data-starting-style:translate-y-8 data-ending-style:opacity-0";

/// 弹层：触发器下方 6px、左对齐，宽度至少同触发器（紧凑触发器至少 184px）。
/// 窄屏且给了 `sheetTitle` 时改成底部抽屉（遮罩、把手、标题），点遮罩或选中即收起；不给则窄屏仍是浮层（日历的年月下拉）
function SelectContent({
  sheetTitle,
  className,
  children,
  ...props
}: SelectPrimitive.Popup.Props & { sheetTitle?: string }) {
  const narrow = useMediaQuery("(max-width: 880px)");
  if (narrow && sheetTitle) {
    return (
      <SelectPrimitive.Portal>
        <SelectPrimitive.Backdrop className="fixed inset-0 z-50 bg-bg-scrim transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0" />
        <SelectPrimitive.Positioner
          alignItemWithTrigger={false}
          className="isolate z-50 top-auto! right-0! bottom-0! left-0! fixed! transform-none!"
        >
          <SelectPrimitive.Popup
            data-slot="select-content"
            data-variant="sheet"
            className={cn(SHEET, className)}
            {...props}
          >
            <div aria-hidden="true" className="mx-auto mb-3 h-1 w-9 rounded-full bg-paper-400" />
            <p className="mb-2 font-semibold text-fg-1 text-md leading-6">{sheetTitle}</p>
            <SelectPrimitive.List>{children}</SelectPrimitive.List>
          </SelectPrimitive.Popup>
        </SelectPrimitive.Positioner>
      </SelectPrimitive.Portal>
    );
  }
  return (
    <SelectPrimitive.Portal>
      <SelectPrimitive.Positioner
        alignItemWithTrigger={false}
        side="bottom"
        align="start"
        sideOffset={6}
        collisionPadding={16}
        className="isolate z-50 outline-none"
      >
        <SelectPrimitive.Popup
          data-slot="select-content"
          data-variant="popover"
          className={cn(POPUP, className)}
          {...props}
        >
          <SelectPrimitive.List>{children}</SelectPrimitive.List>
        </SelectPrimitive.Popup>
      </SelectPrimitive.Positioner>
    </SelectPrimitive.Portal>
  );
}

/// 选项：36 高（抽屉里 48），左侧 16px 勾选位，选中项陶土色勾 + 字重 500，高亮项凹陷底色
function SelectItem({ className, children, ...props }: SelectPrimitive.Item.Props) {
  return (
    <SelectPrimitive.Item
      data-slot="select-item"
      className={cn(
        "flex h-9 w-full cursor-pointer items-center gap-2.5 rounded-md px-2.5 text-left text-base leading-5 text-fg-1 outline-hidden select-none data-highlighted:bg-bg-sunken data-selected:font-medium data-disabled:cursor-not-allowed data-disabled:text-fg-4 in-data-[variant=sheet]:h-12 in-data-[variant=sheet]:px-2 in-data-[variant=sheet]:text-md",
        className,
      )}
      {...props}
    >
      <span className="flex size-4 shrink-0 items-center justify-center text-clay-600">
        <SelectPrimitive.ItemIndicator className="flex">
          <Check className="size-4" aria-hidden="true" />
        </SelectPrimitive.ItemIndicator>
      </span>
      <SelectPrimitive.ItemText className="min-w-0 truncate">{children}</SelectPrimitive.ItemText>
    </SelectPrimitive.Item>
  );
}

/// 分隔线：左右顶到弹层边缘
function SelectSeparator({ className, ...props }: SelectPrimitive.Separator.Props) {
  return (
    <SelectPrimitive.Separator
      data-slot="select-separator"
      className={cn("-mx-1.5 my-1.5 h-px bg-border-subtle", className)}
      {...props}
    />
  );
}

export { Select, SelectContent, SelectItem, SelectSeparator, SelectTrigger, SelectValue };
